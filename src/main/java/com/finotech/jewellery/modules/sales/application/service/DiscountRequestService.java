package com.finotech.jewellery.modules.sales.application.service;

import com.finotech.jewellery.modules.customer.application.CustomerDirectory;
import com.finotech.jewellery.modules.inventory.application.InventoryOperations;
import com.finotech.jewellery.modules.organization.application.OrganizationDirectory;
import com.finotech.jewellery.modules.sales.api.request.DiscountRequestRequests.CreateDiscountRequest;
import com.finotech.jewellery.modules.sales.api.response.DiscountRequestResponse;
import com.finotech.jewellery.modules.sales.domain.entity.DiscountRequest;
import com.finotech.jewellery.modules.sales.domain.enums.DiscountRequestStatus;
import com.finotech.jewellery.modules.sales.infrastructure.repository.DiscountRequestRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.event.DomainEventPublisher;
import com.finotech.jewellery.shared.event.DomainEvents;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.ForbiddenException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.security.AuthenticatedUser;
import com.finotech.jewellery.shared.security.SecurityUtils;
import com.finotech.jewellery.shared.utils.MoneyUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Discount approval workflow.
 *
 * <p>A salesperson who wants to go beyond the branch's discount policy raises
 * a request; a holder of DISCOUNT_APPROVE decides it; the sale that uses it
 * marks it consumed. An approval lapses after a configurable window because
 * a discount agreed against yesterday's metal rate is not an open cheque.
 */
@Service
@RequiredArgsConstructor
public class DiscountRequestService {

    private final DiscountRequestRepository repository;
    private final OrganizationDirectory organizationDirectory;
    private final CustomerDirectory customerDirectory;
    private final InventoryOperations inventory;
    private final AuditService auditService;
    private final DomainEventPublisher events;

    @Value("${jewellery.sales.discount-approval-ttl-hours:24}")
    private long approvalTtlHours;

    // ---------- queries ----------

    @Transactional(readOnly = true)
    public PageResponse<DiscountRequestResponse> search(UUID branchId, DiscountRequestStatus status,
                                                        boolean mine, Pageable pageable) {
        String requestedBy = mine ? SecurityUtils.requireCurrentUser().username() : null;
        return PageResponse.of(repository.search(branchId, status, requestedBy, pageable),
                DiscountRequestResponse::from);
    }

    @Transactional(readOnly = true)
    public DiscountRequestResponse get(UUID id) {
        return DiscountRequestResponse.from(require(id));
    }

    /** Pending requests for the unified approvals list, oldest first. */
    @Transactional(readOnly = true)
    public List<DiscountRequest> pending(UUID branchId, int limit) {
        return repository.findPending(branchId, PageRequest.of(0, limit)).getContent();
    }

    // ---------- raise ----------

    @Transactional
    public DiscountRequestResponse create(CreateDiscountRequest request) {
        if ((request.requestedPercentage() == null) == (request.requestedAmount() == null)) {
            throw new ValidationException(
                    "Provide exactly one of requestedPercentage or requestedAmount");
        }
        if (!organizationDirectory.branchExists(request.branchId())) {
            throw NotFoundException.of("Branch", request.branchId());
        }
        SecurityUtils.requireBranchAccess(request.branchId());
        if (request.customerId() != null) {
            customerDirectory.requireCustomer(request.customerId());
        }
        if (request.jewelleryItemId() != null) {
            inventory.requireItem(request.jewelleryItemId());
        }

        DiscountRequest entity = new DiscountRequest();
        entity.setBranchId(request.branchId());
        entity.setCustomerId(request.customerId());
        entity.setJewelleryItemId(request.jewelleryItemId());
        entity.setQuotationId(request.quotationId());
        entity.setRequestedPercentage(request.requestedPercentage() == null ? null
                : request.requestedPercentage().setScale(2, MoneyUtils.ROUNDING));
        entity.setRequestedAmount(request.requestedAmount() == null ? null
                : MoneyUtils.money(request.requestedAmount()));
        entity.setCurrency(StringUtils.hasText(request.currency())
                ? request.currency().toUpperCase() : null);
        entity.setReason(request.reason().trim());
        entity.setStatus(DiscountRequestStatus.PENDING);
        entity.setExpiresAt(Instant.now().plus(Duration.ofHours(approvalTtlHours)));

        DiscountRequest saved = repository.saveAndFlush(entity);
        auditService.record("DISCOUNT_REQUESTED", "DiscountRequest", saved.getId(), null,
                DiscountRequestResponse.from(saved), saved.getBranchId());
        events.publish(new DomainEvents.DiscountRequested(saved.getId(), saved.getBranchId(),
                saved.getCreatedBy(), saved.getRequestedPercentage(), saved.getRequestedAmount(),
                saved.getJewelleryItemId(), saved.getCustomerId()));
        return DiscountRequestResponse.from(saved);
    }

    // ---------- decide ----------

    @Transactional
    public DiscountRequestResponse approve(UUID id, String note) {
        DiscountRequest request = require(id);
        String approver = SecurityUtils.currentUsername().orElse("system");
        if (request.getStatus() != DiscountRequestStatus.PENDING) {
            throw new ConflictException("Discount request is " + request.getStatus()
                    + " and cannot be approved");
        }
        if (approver.equals(request.getCreatedBy())) {
            throw new ConflictException("A discount request cannot be approved by the person who raised it");
        }
        SecurityUtils.requireBranchAccess(request.getBranchId());
        if (request.isExpired(Instant.now())) {
            throw new ConflictException("Discount request has expired");
        }

        request.setStatus(DiscountRequestStatus.APPROVED);
        request.setDecidedBy(approver);
        request.setDecidedAt(Instant.now());
        request.setDecisionNote(note);
        // The approval window restarts at the decision, so a request that sat
        // in the queue for most of the day is still usable once approved.
        request.setExpiresAt(Instant.now().plus(Duration.ofHours(approvalTtlHours)));

        auditService.record("DISCOUNT_APPROVED", "DiscountRequest", id, null,
                Map.of("approvedBy", approver, "note", String.valueOf(note)),
                request.getBranchId());
        events.publish(new DomainEvents.DiscountDecided(id, request.getBranchId(), true, approver,
                note, request.getCreatedBy()));
        return DiscountRequestResponse.from(request);
    }

    @Transactional
    public DiscountRequestResponse reject(UUID id, String reason) {
        DiscountRequest request = require(id);
        String approver = SecurityUtils.currentUsername().orElse("system");
        if (request.getStatus() != DiscountRequestStatus.PENDING) {
            throw new ConflictException("Only a pending discount request can be rejected");
        }
        if (approver.equals(request.getCreatedBy())) {
            throw new ConflictException("A discount request cannot be decided by the person who raised it");
        }
        SecurityUtils.requireBranchAccess(request.getBranchId());

        request.setStatus(DiscountRequestStatus.REJECTED);
        request.setDecidedBy(approver);
        request.setDecidedAt(Instant.now());
        request.setDecisionNote(reason);

        auditService.record("DISCOUNT_REJECTED", "DiscountRequest", id, null,
                Map.of("reason", String.valueOf(reason)), request.getBranchId());
        events.publish(new DomainEvents.DiscountDecided(id, request.getBranchId(), false, approver,
                reason, request.getCreatedBy()));
        return DiscountRequestResponse.from(request);
    }

    /** The requester withdraws a request that has not been decided yet. */
    @Transactional
    public DiscountRequestResponse cancel(UUID id) {
        DiscountRequest request = require(id);
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        if (!user.superAdmin() && !user.username().equals(request.getCreatedBy())) {
            throw new ForbiddenException("Only the requester can cancel a discount request");
        }
        if (request.getStatus() != DiscountRequestStatus.PENDING) {
            throw new ConflictException("Only a pending discount request can be cancelled");
        }
        request.setStatus(DiscountRequestStatus.CANCELLED);
        auditService.record("DISCOUNT_REQUEST_CANCELLED", "DiscountRequest", id, null, null,
                request.getBranchId());
        return DiscountRequestResponse.from(request);
    }

    // ---------- seam used by SaleService ----------

    /**
     * Resolves an approved request a sale wants to draw on, checking that it
     * still applies: approved, unexpired, same branch, and (where the request
     * named them) the same customer and an item on the sale.
     *
     * @throws ValidationException if the request cannot cover this sale
     */
    @Transactional(readOnly = true)
    public DiscountRequest requireUsableFor(UUID requestId, UUID branchId, UUID customerId,
                                            Collection<UUID> saleItemIds) {
        DiscountRequest request = require(requestId);
        if (request.getStatus() != DiscountRequestStatus.APPROVED) {
            throw new ValidationException("Discount request " + requestId + " is "
                    + request.getStatus() + ", not APPROVED");
        }
        if (request.isExpired(Instant.now())) {
            throw new ValidationException("Discount request " + requestId + " has expired");
        }
        if (!request.getBranchId().equals(branchId)) {
            throw new ValidationException("Discount request was approved for a different branch");
        }
        if (request.getCustomerId() != null && !request.getCustomerId().equals(customerId)) {
            throw new ValidationException("Discount request was approved for a different customer");
        }
        if (request.getJewelleryItemId() != null
                && !saleItemIds.contains(request.getJewelleryItemId())) {
            throw new ValidationException(
                    "Discount request was approved for an item that is not on this sale");
        }
        return request;
    }

    /** Marks the request spent on a sale. */
    @Transactional
    public void consume(UUID requestId, UUID saleId) {
        DiscountRequest request = require(requestId);
        if (request.getStatus() != DiscountRequestStatus.APPROVED) {
            throw new ConflictException("Discount request " + requestId + " is "
                    + request.getStatus() + " and cannot be applied");
        }
        request.setStatus(DiscountRequestStatus.CONSUMED);
        request.setConsumedBySaleId(saleId);
        auditService.record("DISCOUNT_CONSUMED", "DiscountRequest", requestId, null,
                Map.of("saleId", String.valueOf(saleId)), request.getBranchId());
    }

    // ---------- expiry ----------

    /** Lapses every open request whose window has closed. Run by the scheduler. */
    @Transactional
    public int expireOverdue() {
        return repository.expireOverdue(
                EnumSet.of(DiscountRequestStatus.PENDING, DiscountRequestStatus.APPROVED),
                Instant.now());
    }

    // ---------- helpers ----------

    public DiscountRequest require(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> NotFoundException.of("DiscountRequest", id));
    }
}
