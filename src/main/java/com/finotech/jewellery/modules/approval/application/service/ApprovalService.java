package com.finotech.jewellery.modules.approval.application.service;

import com.finotech.jewellery.modules.approval.api.request.ApprovalRequests.DecisionRequest;
import com.finotech.jewellery.modules.approval.api.response.ApprovalResponses.ApprovalItemResponse;
import com.finotech.jewellery.modules.approval.api.response.ApprovalResponses.DecisionResponse;
import com.finotech.jewellery.modules.approval.api.response.ApprovalResponses.InformationRequestResponse;
import com.finotech.jewellery.modules.approval.api.response.ApprovalResponses.PendingCountResponse;
import com.finotech.jewellery.modules.approval.domain.entity.ApprovalDecisionRecord;
import com.finotech.jewellery.modules.approval.domain.entity.ApprovalInformationRequest;
import com.finotech.jewellery.modules.approval.domain.enums.ApprovalDecision;
import com.finotech.jewellery.modules.approval.domain.enums.ApprovalType;
import com.finotech.jewellery.modules.approval.infrastructure.repository.ApprovalDecisionRepository;
import com.finotech.jewellery.modules.approval.infrastructure.repository.ApprovalInformationRequestRepository;
import com.finotech.jewellery.modules.exchange.application.service.ExchangeService;
import com.finotech.jewellery.modules.exchange.domain.entity.ExchangeIntake;
import com.finotech.jewellery.modules.exchange.domain.enums.ExchangeStatus;
import com.finotech.jewellery.modules.exchange.infrastructure.repository.ExchangeIntakeRepository;
import com.finotech.jewellery.modules.inventory.api.request.RejectMovementRequest;
import com.finotech.jewellery.modules.inventory.application.service.InventoryMovementService;
import com.finotech.jewellery.modules.inventory.domain.entity.InventoryMovement;
import com.finotech.jewellery.modules.inventory.domain.enums.MovementStatus;
import com.finotech.jewellery.modules.inventory.infrastructure.repository.InventoryMovementRepository;
import com.finotech.jewellery.modules.organization.application.OrganizationDirectory;
import com.finotech.jewellery.modules.procurement.application.service.GoodsReceiptService;
import com.finotech.jewellery.modules.procurement.application.service.PurchaseOrderService;
import com.finotech.jewellery.modules.procurement.domain.entity.GoodsReceipt;
import com.finotech.jewellery.modules.procurement.domain.entity.PurchaseOrder;
import com.finotech.jewellery.modules.procurement.domain.entity.PurchaseRequisition;
import com.finotech.jewellery.modules.procurement.domain.enums.GoodsReceiptStatus;
import com.finotech.jewellery.modules.procurement.domain.enums.PurchaseOrderStatus;
import com.finotech.jewellery.modules.procurement.domain.enums.RequisitionStatus;
import com.finotech.jewellery.modules.procurement.infrastructure.repository.GoodsReceiptRepository;
import com.finotech.jewellery.modules.procurement.infrastructure.repository.PurchaseOrderRepository;
import com.finotech.jewellery.modules.procurement.infrastructure.repository.PurchaseRequisitionRepository;
import com.finotech.jewellery.modules.sales.application.service.DiscountRequestService;
import com.finotech.jewellery.modules.sales.domain.entity.DiscountRequest;
import com.finotech.jewellery.modules.warehouse.application.service.WarehouseService;
import com.finotech.jewellery.modules.warehouse.domain.entity.StockCount;
import com.finotech.jewellery.modules.warehouse.domain.enums.StockCountStatus;
import com.finotech.jewellery.modules.warehouse.infrastructure.repository.StockCountRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.event.DomainEventPublisher;
import com.finotech.jewellery.shared.event.DomainEvents;
import com.finotech.jewellery.shared.exception.BusinessException;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.ErrorCode;
import com.finotech.jewellery.shared.exception.ForbiddenException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.security.AuthenticatedUser;
import com.finotech.jewellery.shared.security.SecurityUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * One queue for everything that waits on somebody's sign-off.
 *
 * <p>Each module keeps its own workflow and its own approve/reject rules; this
 * service only reads their pending records into a common shape and dispatches
 * a decision back to the owning service. It adds two things the modules lack:
 * a "tell me more" step that leaves the record's status untouched, and an
 * idempotency key on the decision itself so a retried tap on a phone cannot
 * approve twice.
 *
 * <p>Scoping is done here rather than in the controller because the type of a
 * decision is only known at runtime: the caller must hold the approve
 * permission for that type and have access to the record's branch.
 */
@Service
@RequiredArgsConstructor
public class ApprovalService {

    /** Rows per type pulled from the database before scoping; the list is capped anyway. */
    private static final int FETCH_CAP = 200;
    private static final int LIST_CAP = 200;

    private final ApprovalInformationRequestRepository informationRepository;
    private final ApprovalDecisionRepository decisionRepository;

    private final InventoryMovementRepository movementRepository;
    private final PurchaseOrderRepository orderRepository;
    private final PurchaseRequisitionRepository requisitionRepository;
    private final ExchangeIntakeRepository exchangeRepository;
    private final StockCountRepository stockCountRepository;
    private final GoodsReceiptRepository receiptRepository;

    private final InventoryMovementService movementService;
    private final PurchaseOrderService purchaseOrderService;
    private final ExchangeService exchangeService;
    private final WarehouseService warehouseService;
    private final GoodsReceiptService goodsReceiptService;
    private final DiscountRequestService discountRequestService;

    private final OrganizationDirectory organizationDirectory;
    private final AuditService auditService;
    private final DomainEventPublisher events;

    // ---------- pending list ----------

    @Transactional(readOnly = true)
    public List<ApprovalItemResponse> pending(UUID branchId, ApprovalType typeFilter) {
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        if (branchId != null && !user.hasAccessToBranch(branchId)) {
            throw new ForbiddenException("No access to branch " + branchId);
        }

        List<ApprovalItemResponse> rows = new ArrayList<>();
        for (ApprovalType type : ApprovalType.values()) {
            if (typeFilter != null && type != typeFilter) {
                continue;
            }
            if (!canApprove(user, type)) {
                continue;
            }
            List<ApprovalItemResponse> ofType = pendingOfType(type, branchId).stream()
                    .filter(row -> user.hasAccessToBranch(row.branchId()))
                    .toList();
            rows.addAll(flag(type, ofType));
        }

        rows.sort(Comparator.comparing(ApprovalItemResponse::requestedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return rows.size() > LIST_CAP ? rows.subList(0, LIST_CAP) : rows;
    }

    @Transactional(readOnly = true)
    public PendingCountResponse pendingCount(UUID branchId) {
        Map<ApprovalType, Long> byType = new EnumMap<>(ApprovalType.class);
        for (ApprovalType type : ApprovalType.values()) {
            byType.put(type, 0L);
        }
        for (ApprovalItemResponse row : pending(branchId, null)) {
            byType.merge(row.type(), 1L, Long::sum);
        }
        long total = byType.values().stream().mapToLong(Long::longValue).sum();
        return new PendingCountResponse(total, byType);
    }

    /** Adds branch names and the open-information flag in two batch lookups. */
    private List<ApprovalItemResponse> flag(ApprovalType type, List<ApprovalItemResponse> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        Set<UUID> branchIds = new HashSet<>();
        Set<UUID> ids = new HashSet<>();
        rows.forEach(r -> {
            if (r.branchId() != null) {
                branchIds.add(r.branchId());
            }
            ids.add(r.id());
        });
        Map<UUID, String> branchNames = organizationDirectory.branchNames(branchIds);
        Set<UUID> withOpenQuestions = new HashSet<>();
        informationRepository.findAllByApprovalTypeAndReferenceIdInAndAnsweredAtIsNull(type, ids)
                .forEach(q -> withOpenQuestions.add(q.getReferenceId()));
        return rows.stream()
                .map(r -> r.withFlags(r.branchId() == null ? null : branchNames.get(r.branchId()),
                        withOpenQuestions.contains(r.id())))
                .toList();
    }

    private List<ApprovalItemResponse> pendingOfType(ApprovalType type, UUID branchId) {
        Pageable oldestFirst = PageRequest.of(0, FETCH_CAP, Sort.by("createdAt").ascending());
        Pageable capped = PageRequest.of(0, FETCH_CAP);
        return switch (type) {
            case TRANSFER -> {
                // Scoped in the query, not after it: with more pending transfers
                // platform-wide than the cap, a post-filter could drop the oldest
                // of this caller's branches while keeping other people's.
                AuthenticatedUser user = SecurityUtils.requireCurrentUser();
                if (branchId == null && !user.superAdmin() && user.branchIds().isEmpty()) {
                    // No branches means nothing to approve, not everything.
                    yield List.of();
                }
                Set<UUID> scope = branchId != null ? Set.of(branchId)
                        : user.superAdmin() ? Set.of() : user.branchIds();
                yield movementRepository
                        .findPendingApprovalInBranches(SecurityUtils.currentCompanyIdOrNull(), scope,
                                PageRequest.of(0, FETCH_CAP))
                        .stream()
                        .map(this::transferRow)
                        .toList();
            }
            case PURCHASE_ORDER -> orderRepository
                    .search(PurchaseOrderStatus.PENDING_APPROVAL, null, branchId, oldestFirst)
                    .getContent().stream().map(this::orderRow).toList();
            case REQUISITION -> requisitionRepository
                    .search(RequisitionStatus.PENDING_APPROVAL, branchId, oldestFirst)
                    .getContent().stream().map(this::requisitionRow).toList();
            case EXCHANGE -> exchangeRepository
                    .search(ExchangeStatus.PENDING_APPROVAL, null, null, branchId, capped)
                    .getContent().stream().map(this::exchangeRow).toList();
            case STOCK_COUNT -> {
                // A count awaiting its first review, or approved once and waiting
                // for the second signature a vault or a variance requires.
                List<StockCount> counts = new ArrayList<>(stockCountRepository
                        .search(StockCountStatus.PENDING_REVIEW, null, branchId, capped).getContent());
                counts.addAll(stockCountRepository
                        .search(StockCountStatus.APPROVED, null, branchId, capped).getContent());
                yield counts.stream().map(this::stockCountRow).toList();
            }
            case GOODS_RECEIPT -> receiptRepository
                    .search(GoodsReceiptStatus.PENDING_QUALITY_CHECK, null, branchId, oldestFirst)
                    .getContent().stream().map(this::receiptRow).toList();
            case DISCOUNT -> discountRequestService.pending(branchId, FETCH_CAP).stream()
                    .map(this::discountRow).toList();
        };
    }

    // ---------- row shapes ----------

    private ApprovalItemResponse transferRow(InventoryMovement m) {
        Map<UUID, String> names = new HashMap<>();
        Function<UUID, String> locationName = id -> id == null ? "—"
                : names.computeIfAbsent(id, i -> {
                    try {
                        return organizationDirectory.requireLocation(i).name();
                    } catch (BusinessException ex) {
                        return String.valueOf(i);
                    }
                });
        int items = m.getLines().size();
        String summary = items + (items == 1 ? " item · " : " items · ")
                + locationName.apply(m.getFromLocationId()) + " → "
                + locationName.apply(m.getToLocationId());
        return new ApprovalItemResponse(ApprovalType.TRANSFER, m.getId(), m.getReferenceNumber(),
                summary, m.getToBranchId(), null, m.getCreatedBy(), m.getCreatedAt(), null, null,
                m.getApprovedBy() != null, false);
    }

    private ApprovalItemResponse orderRow(PurchaseOrder o) {
        int lines = o.getLines().size();
        String summary = lines + (lines == 1 ? " line" : " lines")
                + (o.getExpectedDeliveryDate() != null ? " · due " + o.getExpectedDeliveryDate() : "");
        return new ApprovalItemResponse(ApprovalType.PURCHASE_ORDER, o.getId(), o.getOrderNumber(),
                summary, o.getBranchId(), null, o.getCreatedBy(), o.getCreatedAt(),
                o.getEstimatedTotal(), o.getCurrency(), false, false);
    }

    private ApprovalItemResponse requisitionRow(PurchaseRequisition r) {
        int lines = r.getLines().size();
        String summary = lines + (lines == 1 ? " line" : " lines")
                + (r.getRequiredBy() != null ? " · needed by " + r.getRequiredBy() : "");
        return new ApprovalItemResponse(ApprovalType.REQUISITION, r.getId(), r.getReferenceNumber(),
                summary, r.getBranchId(), null, r.getCreatedBy(), r.getCreatedAt(), null, null,
                false, false);
    }

    private ApprovalItemResponse exchangeRow(ExchangeIntake e) {
        String summary = e.getExchangeType() + " · " + e.getDescription()
                + (e.getNetWeight() != null ? " · " + e.getNetWeight() + " g" : "");
        return new ApprovalItemResponse(ApprovalType.EXCHANGE, e.getId(), e.getReferenceNumber(),
                summary, e.getBranchId(), null, e.getValuedBy() != null ? e.getValuedBy() : e.getCreatedBy(),
                e.getValuedAt() != null ? e.getValuedAt() : e.getCreatedAt(),
                e.getNetValuation(), e.getCurrency(), false, false);
    }

    private ApprovalItemResponse stockCountRow(StockCount c) {
        String summary = c.getCountedCount() + " of " + c.getExpectedCount() + " counted"
                + (c.getMissingCount() > 0 ? " · " + c.getMissingCount() + " missing" : "")
                + (c.getUnexpectedCount() > 0 ? " · " + c.getUnexpectedCount() + " unexpected" : "");
        return new ApprovalItemResponse(ApprovalType.STOCK_COUNT, c.getId(), c.getReferenceNumber(),
                summary, c.getBranchId(), null,
                c.getCountedBy() != null ? c.getCountedBy() : c.getCreatedBy(),
                c.getCountedAt() != null ? c.getCountedAt() : c.getCreatedAt(), null, null,
                c.getStatus() == StockCountStatus.APPROVED, false);
    }

    private ApprovalItemResponse receiptRow(GoodsReceipt g) {
        int lines = g.getLines().size();
        String summary = lines + (lines == 1 ? " line" : " lines") + " · received " + g.getReceiptDate()
                + (StringUtils.hasText(g.getSupplierDeliveryNote())
                        ? " · DN " + g.getSupplierDeliveryNote() : "");
        return new ApprovalItemResponse(ApprovalType.GOODS_RECEIPT, g.getId(), g.getReceiptNumber(),
                summary, g.getBranchId(), null, g.getCreatedBy(), g.getCreatedAt(), null, null,
                false, false);
    }

    private ApprovalItemResponse discountRow(DiscountRequest d) {
        String ask = d.isPercentage()
                ? d.getRequestedPercentage().stripTrailingZeros().toPlainString() + "%"
                : d.getRequestedAmount() + (d.getCurrency() != null ? " " + d.getCurrency() : "");
        String summary = ask + " · " + d.getReason();
        return new ApprovalItemResponse(ApprovalType.DISCOUNT, d.getId(),
                "DR-" + d.getId().toString().substring(0, 8).toUpperCase(), summary,
                d.getBranchId(), null, d.getCreatedBy(), d.getCreatedAt(),
                d.getRequestedAmount(), d.getCurrency(), false, false);
    }

    // ---------- decisions ----------

    @Transactional
    public DecisionResponse decide(ApprovalType type, UUID id, DecisionRequest request,
                                   String idempotencyKey) {
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        if (!canApprove(user, type)) {
            throw new ForbiddenException("Deciding a " + type + " requires " + type.approvePermission());
        }
        if (request.decision().requiresReason() && !StringUtils.hasText(request.reason())) {
            throw new ValidationException("A reason is required for " + request.decision());
        }

        if (StringUtils.hasText(idempotencyKey)) {
            var replay = decisionRepository.findByIdempotencyKey(idempotencyKey);
            if (replay.isPresent()) {
                ApprovalDecisionRecord stored = replay.get();
                boolean same = stored.getApprovalType() == type
                        && stored.getReferenceId().equals(id)
                        && stored.getDecision() == request.decision()
                        && Objects.equals(stored.getReason(), request.reason());
                if (!same) {
                    throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT,
                            "X-Idempotency-Key was already used for a different decision");
                }
                return DecisionResponse.from(stored);
            }
        }

        Snapshot before = snapshot(type, id);
        SecurityUtils.requireBranchAccess(before.branchId());

        String status = switch (request.decision()) {
            case APPROVE -> approve(type, id, request.reason(), user.username(), before);
            case REJECT -> reject(type, id, request.reason());
            case REQUEST_INFO -> {
                requestInformation(type, id, before, request.reason(), user.username());
                yield before.status();
            }
        };

        ApprovalDecisionRecord record = new ApprovalDecisionRecord();
        record.setApprovalType(type);
        record.setReferenceId(id);
        record.setDecision(request.decision());
        record.setResultStatus(status);
        record.setDecidedBy(user.username());
        record.setReason(request.reason());
        record.setIdempotencyKey(StringUtils.hasText(idempotencyKey) ? idempotencyKey : null);
        ApprovalDecisionRecord saved = decisionRepository.saveAndFlush(record);

        auditService.record("APPROVAL_DECISION", type.entityType(), id, null,
                Map.of("decision", request.decision(), "status", status,
                        "reason", String.valueOf(request.reason())),
                before.branchId());
        return DecisionResponse.from(saved);
    }

    /**
     * Approving something the same user has already approved is treated as a
     * repeat of the same intent, not a new decision: the current state comes
     * back instead of a conflict. The check happens before the module is
     * called, because an exception thrown inside the module's own transaction
     * would mark the shared transaction rollback-only. A second, different
     * approver still goes through to the module, where dual authorization lives.
     */
    private String approve(ApprovalType type, UUID id, String note, String approver,
                           Snapshot before) {
        if (before.approvedBySameUser(approver)) {
            return before.status();
        }
        return switch (type) {
            case TRANSFER -> movementService.approve(id).status().name();
            case PURCHASE_ORDER -> purchaseOrderService.approveOrder(id).status().name();
            case REQUISITION -> purchaseOrderService.approveRequisition(id).status().name();
            case EXCHANGE -> exchangeService.approve(id).status().name();
            case STOCK_COUNT -> warehouseService.approveCount(id).status().name();
            case GOODS_RECEIPT -> goodsReceiptService.accept(id).status().name();
            case DISCOUNT -> discountRequestService.approve(id, note).status().name();
        };
    }

    private String reject(ApprovalType type, UUID id, String reason) {
        return switch (type) {
            case TRANSFER -> movementService.reject(id, new RejectMovementRequest(reason)).status().name();
            case PURCHASE_ORDER -> purchaseOrderService.rejectOrder(id, reason).status().name();
            case REQUISITION -> purchaseOrderService.rejectRequisition(id, reason).status().name();
            case EXCHANGE -> exchangeService.reject(id, reason).status().name();
            case STOCK_COUNT -> warehouseService.cancelCount(id, reason).status().name();
            case GOODS_RECEIPT -> goodsReceiptService.reject(id, reason).status().name();
            case DISCOUNT -> discountRequestService.reject(id, reason).status().name();
        };
    }

    // ---------- information requests ----------

    private void requestInformation(ApprovalType type, UUID id, Snapshot record, String message,
                                    String requestedBy) {
        ApprovalInformationRequest question = new ApprovalInformationRequest();
        question.setApprovalType(type);
        question.setReferenceId(id);
        question.setRequestedBy(requestedBy);
        question.setMessage(message.trim());
        informationRepository.saveAndFlush(question);

        events.publish(new DomainEvents.ApprovalInformationRequested(record.branchId(), type.name(),
                id, record.reference(), question.getMessage(), requestedBy, record.creator()));
    }

    @Transactional(readOnly = true)
    public List<InformationRequestResponse> information(ApprovalType type, UUID id) {
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        if (!canApprove(user, type) && !canCreate(user, type)) {
            throw new ForbiddenException("No permission to view " + type + " approvals");
        }
        Snapshot record = snapshot(type, id);
        if (!user.hasAccessToBranch(record.branchId())) {
            throw new ForbiddenException("No access to branch " + record.branchId());
        }
        return informationRepository.findAllByApprovalTypeAndReferenceIdOrderByCreatedAtAsc(type, id)
                .stream().map(InformationRequestResponse::from).toList();
    }

    @Transactional
    public InformationRequestResponse answer(ApprovalType type, UUID id, UUID requestId,
                                             String answer) {
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        if (!canCreate(user, type)) {
            throw new ForbiddenException("Answering a " + type + " question requires "
                    + type.createPermission());
        }
        ApprovalInformationRequest question = informationRepository.findById(requestId)
                .orElseThrow(() -> NotFoundException.of("ApprovalInformationRequest", requestId));
        if (question.getApprovalType() != type || !question.getReferenceId().equals(id)) {
            throw NotFoundException.of("ApprovalInformationRequest", requestId);
        }
        if (!question.isOpen()) {
            throw new ConflictException("This question has already been answered");
        }
        Snapshot record = snapshot(type, id);
        SecurityUtils.requireBranchAccess(record.branchId());

        question.setAnswer(answer.trim());
        question.setAnsweredBy(user.username());
        question.setAnsweredAt(Instant.now());

        auditService.record("APPROVAL_INFO_ANSWERED", type.entityType(), id, null,
                Map.of("requestId", requestId, "answeredBy", user.username()), record.branchId());
        events.publish(new DomainEvents.ApprovalInformationAnswered(record.branchId(), type.name(),
                id, record.reference(), question.getAnswer(), user.username(),
                question.getRequestedBy()));
        return InformationRequestResponse.from(question);
    }

    // ---------- helpers ----------

    private static boolean canApprove(AuthenticatedUser user, ApprovalType type) {
        return user.superAdmin() || user.permissions().contains(type.approvePermission());
    }

    private static boolean canCreate(AuthenticatedUser user, ApprovalType type) {
        return user.superAdmin() || user.permissions().contains(type.createPermission());
    }

    /** The little the unified layer needs to know about a record of any type. */
    private record Snapshot(String status, UUID branchId, String reference, String creator,
                            String approvedBy, String secondApprovedBy) {

        boolean approvedBySameUser(String user) {
            return user != null && (user.equals(approvedBy) || user.equals(secondApprovedBy));
        }
    }

    private Snapshot snapshot(ApprovalType type, UUID id) {
        return switch (type) {
            case TRANSFER -> {
                InventoryMovement m = movementRepository.findById(id)
                        .orElseThrow(() -> NotFoundException.of("InventoryMovement", id));
                yield new Snapshot(m.getStatus().name(), m.getToBranchId(), m.getReferenceNumber(),
                        m.getCreatedBy(), m.getApprovedBy(), m.getSecondApprovedBy());
            }
            case PURCHASE_ORDER -> {
                PurchaseOrder o = orderRepository.findById(id)
                        .orElseThrow(() -> NotFoundException.of("PurchaseOrder", id));
                yield new Snapshot(o.getStatus().name(), o.getBranchId(), o.getOrderNumber(),
                        o.getCreatedBy(), o.getApprovedBy(), null);
            }
            case REQUISITION -> {
                PurchaseRequisition r = requisitionRepository.findById(id)
                        .orElseThrow(() -> NotFoundException.of("PurchaseRequisition", id));
                yield new Snapshot(r.getStatus().name(), r.getBranchId(), r.getReferenceNumber(),
                        r.getCreatedBy(), r.getApprovedBy(), null);
            }
            case EXCHANGE -> {
                ExchangeIntake e = exchangeRepository.findById(id)
                        .orElseThrow(() -> NotFoundException.of("ExchangeIntake", id));
                yield new Snapshot(e.getStatus().name(), e.getBranchId(), e.getReferenceNumber(),
                        e.getValuedBy() != null ? e.getValuedBy() : e.getCreatedBy(),
                        e.getApprovedBy(), null);
            }
            case STOCK_COUNT -> {
                StockCount c = stockCountRepository.findById(id)
                        .orElseThrow(() -> NotFoundException.of("StockCount", id));
                yield new Snapshot(c.getStatus().name(), c.getBranchId(), c.getReferenceNumber(),
                        c.getCountedBy() != null ? c.getCountedBy() : c.getCreatedBy(),
                        c.getApprovedBy(), c.getSecondApprovedBy());
            }
            case GOODS_RECEIPT -> {
                GoodsReceipt g = receiptRepository.findById(id)
                        .orElseThrow(() -> NotFoundException.of("GoodsReceipt", id));
                yield new Snapshot(g.getStatus().name(), g.getBranchId(), g.getReceiptNumber(),
                        g.getCreatedBy(), g.getQualityCheckedBy(), null);
            }
            case DISCOUNT -> {
                DiscountRequest d = discountRequestService.require(id);
                yield new Snapshot(d.getStatus().name(), d.getBranchId(),
                        "DR-" + d.getId().toString().substring(0, 8).toUpperCase(),
                        d.getCreatedBy(), d.getDecidedBy(), null);
            }
        };
    }
}
