package com.finotech.jewellery.modules.repair.application.service;

import com.finotech.jewellery.modules.customer.application.CustomerDirectory;
import com.finotech.jewellery.modules.inventory.api.request.ChangeStatusRequest;
import com.finotech.jewellery.modules.inventory.application.service.JewelleryItemService;
import com.finotech.jewellery.modules.inventory.domain.enums.ItemStatus;
import com.finotech.jewellery.modules.repair.api.request.RepairRequests;
import com.finotech.jewellery.modules.repair.api.response.RepairResponse;
import com.finotech.jewellery.modules.repair.domain.entity.RepairRequest;
import com.finotech.jewellery.modules.repair.domain.entity.RepairStatusHistory;
import com.finotech.jewellery.modules.repair.domain.enums.RepairStatus;
import com.finotech.jewellery.modules.repair.infrastructure.repository.RepairRequestRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.event.DomainEventPublisher;
import com.finotech.jewellery.shared.event.DomainEvents;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.security.SecurityUtils;
import com.finotech.jewellery.shared.utils.CodeGenerator;
import com.finotech.jewellery.shared.utils.MoneyUtils;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repair intake through delivery (section 16).
 *
 * <p>Two rules shape this module. Chargeable work never starts before the
 * customer accepts the estimate, and a piece the business sold is moved to
 * {@code UNDER_REPAIR} in inventory while it is in the workshop — which is what
 * stops it being transferred or sold to someone else in the meantime.
 */
@Service
@RequiredArgsConstructor
public class RepairService {

    private final RepairRequestRepository repairRepository;
    private final JewelleryItemService itemService;
    private final CustomerDirectory customerDirectory;
    private final AuditService auditService;
    private final DomainEventPublisher events;

    @Transactional(readOnly = true)
    public PageResponse<RepairResponse> search(RepairStatus status, UUID customerId, UUID branchId,
                                               String assignedTo, Pageable pageable) {
        return PageResponse.of(
                repairRepository.search(status, customerId, branchId, assignedTo, pageable),
                RepairResponse::from);
    }

    @Transactional(readOnly = true)
    public RepairResponse get(UUID id) {
        return RepairResponse.withHistory(requireRepair(id));
    }

    @Transactional(readOnly = true)
    public List<RepairResponse> overdue(UUID branchId) {
        return repairRepository.findOverdue(branchId, LocalDate.now()).stream()
                .map(RepairResponse::from).toList();
    }

    /** Step 1: the piece is taken in and its condition recorded. */
    @Transactional
    public RepairResponse receive(RepairRequests.ReceiveRequest request) {
        CustomerDirectory.CustomerView customer =
                customerDirectory.requireTransactableCustomer(request.customerId());
        SecurityUtils.requireBranchAccess(request.branchId());

        RepairRequest repair = new RepairRequest();
        repair.setRequestNumber(CodeGenerator.reference("RPR"));
        repair.setCustomerId(customer.id());
        repair.setBranchId(request.branchId());
        repair.setJewelleryItemId(request.jewelleryItemId());
        repair.setItemDescription(request.itemDescription().trim());
        repair.setReportedProblem(request.reportedProblem().trim());
        repair.setConditionOnArrival(request.conditionOnArrival());
        repair.setReceivedWeight(MoneyUtils.weight(request.receivedWeight()));
        repair.setConditionPhotoKeys(request.conditionPhotoKeys());
        repair.setPromisedDate(request.promisedDate());
        repair.setReceivedDate(LocalDate.now());
        repair.setNotes(request.notes());
        repair.setStatus(RepairStatus.RECEIVED);

        RepairRequest saved = repairRepository.save(repair);
        recordHistory(saved, null, RepairStatus.RECEIVED, "Received for repair");

        // A piece we sold is taken out of circulation while it is with us.
        if (request.jewelleryItemId() != null) {
            itemService.changeStatus(request.jewelleryItemId(), new ChangeStatusRequest(
                    ItemStatus.UNDER_REPAIR, "Repair " + saved.getRequestNumber()));
        }

        auditService.record("REPAIR_RECEIVED", "RepairRequest", saved.getId(), null,
                Map.of("customerId", String.valueOf(customer.id()),
                        "requestNumber", saved.getRequestNumber()),
                saved.getBranchId());
        return RepairResponse.from(saved);
    }

    /** Step 2: the workshop inspects and records what it found. */
    @Transactional
    public RepairResponse inspect(UUID id, RepairRequests.InspectionRequest request) {
        RepairRequest repair = requireRepair(id);
        repair.transitionTo(RepairStatus.INSPECTION);
        if (request.conditionNotes() != null) {
            repair.setConditionOnArrival(request.conditionNotes());
        }
        recordHistory(repair, RepairStatus.RECEIVED, RepairStatus.INSPECTION, request.findings());
        auditService.record("REPAIR_INSPECTED", "RepairRequest", id, null,
                Map.of("findings", request.findings()), repair.getBranchId());
        return RepairResponse.from(repair);
    }

    /** Step 3: the estimate is prepared and put to the customer. */
    @Transactional
    public RepairResponse estimate(UUID id, RepairRequests.EstimateRequest request) {
        RepairRequest repair = requireRepair(id);
        RepairStatus from = repair.getStatus();
        repair.transitionTo(RepairStatus.ESTIMATION);

        repair.setEstimatedCost(MoneyUtils.money(request.estimatedCost()));
        repair.setEstimatedDays(request.estimatedDays());
        repair.setEstimateNotes(request.estimateNotes());
        if (request.estimatedDays() != null && repair.getPromisedDate() == null) {
            repair.setPromisedDate(LocalDate.now().plusDays(request.estimatedDays()));
        }
        recordHistory(repair, from, RepairStatus.ESTIMATION,
                "Estimated at " + repair.getEstimatedCost());

        repair.transitionTo(RepairStatus.APPROVAL_PENDING);
        recordHistory(repair, RepairStatus.ESTIMATION, RepairStatus.APPROVAL_PENDING,
                "Awaiting customer decision");

        auditService.record("REPAIR_ESTIMATED", "RepairRequest", id, null,
                Map.of("estimatedCost", repair.getEstimatedCost()), repair.getBranchId());
        return RepairResponse.from(repair);
    }

    /**
     * Step 4: the customer's decision. Declining ends the job without work being
     * done; the piece is then handed back.
     */
    @Transactional
    public RepairResponse recordCustomerDecision(UUID id,
                                                 RepairRequests.CustomerDecisionRequest request) {
        RepairRequest repair = requireRepair(id);
        if (repair.getStatus() != RepairStatus.APPROVAL_PENDING) {
            throw new ValidationException(
                    "The customer can only decide once an estimate has been given");
        }
        repair.setCustomerResponseAt(Instant.now());

        if (request.approved()) {
            repair.setCustomerApproved(true);
            repair.transitionTo(RepairStatus.IN_PROGRESS);
            recordHistory(repair, RepairStatus.APPROVAL_PENDING, RepairStatus.IN_PROGRESS,
                    "Customer approved the estimate");
        } else {
            repair.setCustomerApproved(false);
            repair.setDeclineReason(request.declineReason());
            repair.transitionTo(RepairStatus.DECLINED);
            recordHistory(repair, RepairStatus.APPROVAL_PENDING, RepairStatus.DECLINED,
                    request.declineReason());
        }

        auditService.record("REPAIR_CUSTOMER_DECISION", "RepairRequest", id, null,
                Map.of("approved", request.approved(),
                        "reason", String.valueOf(request.declineReason())),
                repair.getBranchId());
        return RepairResponse.from(repair);
    }

    /** Assigns the job to a craftsperson. */
    @Transactional
    public RepairResponse assign(UUID id, RepairRequests.AssignRequest request) {
        RepairRequest repair = requireRepair(id);
        if (repair.getStatus() != RepairStatus.IN_PROGRESS) {
            throw new ValidationException("Only an approved, in-progress repair can be assigned");
        }
        repair.setAssignedTo(request.assignedTo().trim());
        repair.setAssignedAt(Instant.now());
        recordHistory(repair, RepairStatus.IN_PROGRESS, RepairStatus.IN_PROGRESS,
                "Assigned to " + repair.getAssignedTo());
        return RepairResponse.from(repair);
    }

    /** Step 5: work finished, sent for quality check. */
    @Transactional
    public RepairResponse completeWork(UUID id, RepairRequests.CompleteWorkRequest request) {
        RepairRequest repair = requireRepair(id);
        repair.transitionTo(RepairStatus.QUALITY_CHECK);
        if (request.finalCost() != null) {
            repair.setFinalCost(MoneyUtils.money(request.finalCost()));
        }
        recordHistory(repair, RepairStatus.IN_PROGRESS, RepairStatus.QUALITY_CHECK, request.notes());
        return RepairResponse.from(repair);
    }

    /**
     * Step 6: quality check. Failing sends the job back to the bench rather than
     * letting substandard work reach the customer.
     */
    @Transactional
    public RepairResponse qualityCheck(UUID id, RepairRequests.QualityCheckRequest request) {
        RepairRequest repair = requireRepair(id);
        if (repair.getStatus() != RepairStatus.QUALITY_CHECK) {
            throw new ValidationException("This repair is not awaiting quality check");
        }

        if (request.passed()) {
            repair.transitionTo(RepairStatus.READY);
            repair.setReadyAt(Instant.now());
            recordHistory(repair, RepairStatus.QUALITY_CHECK, RepairStatus.READY, request.notes());

            events.publish(new DomainEvents.RepairReady(repair.getId(), repair.getCustomerId(),
                    repair.getBranchId(), repair.getRequestNumber()));
        } else {
            repair.transitionTo(RepairStatus.IN_PROGRESS);
            recordHistory(repair, RepairStatus.QUALITY_CHECK, RepairStatus.IN_PROGRESS,
                    "Quality check failed: " + request.notes());
        }

        auditService.record("REPAIR_QUALITY_CHECK", "RepairRequest", id, null,
                Map.of("passed", request.passed()), repair.getBranchId());
        return RepairResponse.from(repair);
    }

    /**
     * Step 7: the piece goes back to the customer. A piece we track returns to
     * stock only if the customer does not own it; a customer-owned piece simply
     * leaves the workshop.
     */
    @Transactional
    public RepairResponse deliver(UUID id, RepairRequests.DeliverRequest request) {
        RepairRequest repair = requireRepair(id);
        RepairStatus from = repair.getStatus();
        repair.transitionTo(RepairStatus.DELIVERED);

        repair.setDeliveredTo(request.deliveredTo().trim());
        repair.setDeliveredWeight(MoneyUtils.weight(request.deliveredWeight()));
        repair.setDeliveredAt(Instant.now());
        recordHistory(repair, from, RepairStatus.DELIVERED,
                "Delivered to " + repair.getDeliveredTo());

        if (repair.getJewelleryItemId() != null) {
            // The item was moved to UNDER_REPAIR on intake; hand it back to its owner.
            itemService.changeStatus(repair.getJewelleryItemId(), new ChangeStatusRequest(
                    ItemStatus.SOLD, "Returned to customer after repair "
                    + repair.getRequestNumber()));
        }

        auditService.record("REPAIR_DELIVERED", "RepairRequest", id, null,
                Map.of("deliveredTo", repair.getDeliveredTo(),
                        "finalCost", String.valueOf(repair.getFinalCost())),
                repair.getBranchId());
        return RepairResponse.from(repair);
    }

    @Transactional
    public RepairResponse cancel(UUID id, String reason) {
        RepairRequest repair = requireRepair(id);
        RepairStatus from = repair.getStatus();
        repair.transitionTo(RepairStatus.CANCELLED);
        recordHistory(repair, from, RepairStatus.CANCELLED, reason);

        if (repair.getJewelleryItemId() != null) {
            itemService.changeStatus(repair.getJewelleryItemId(), new ChangeStatusRequest(
                    ItemStatus.SOLD, "Repair cancelled: " + reason));
        }
        auditService.record("REPAIR_CANCELLED", "RepairRequest", id, null,
                Map.of("reason", String.valueOf(reason)), repair.getBranchId());
        return RepairResponse.from(repair);
    }

    // ---------- helpers ----------

    private RepairRequest requireRepair(UUID id) {
        return repairRepository.findWithHistoryById(id)
                .orElseThrow(() -> NotFoundException.of("RepairRequest", id));
    }

    private void recordHistory(RepairRequest repair, RepairStatus from, RepairStatus to,
                               String notes) {
        RepairStatusHistory entry = new RepairStatusHistory();
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setPerformedBy(SecurityUtils.currentUsername().orElse("system"));
        entry.setNotes(notes);
        entry.setOccurredAt(Instant.now());
        repair.addHistory(entry);
    }
}
