package com.finotech.jewellery.modules.crm.application.service;

import com.finotech.jewellery.modules.crm.api.request.CrmRequests;
import com.finotech.jewellery.modules.crm.api.response.CrmResponses.ActivityResponse;
import com.finotech.jewellery.modules.crm.api.response.CrmResponses.Customer360Response;
import com.finotech.jewellery.modules.crm.api.response.CrmResponses.FollowUpResponse;
import com.finotech.jewellery.modules.crm.domain.entity.CustomerActivity;
import com.finotech.jewellery.modules.crm.domain.entity.FollowUp;
import com.finotech.jewellery.modules.crm.domain.enums.ActivityType;
import com.finotech.jewellery.modules.crm.domain.enums.FollowUpStatus;
import com.finotech.jewellery.modules.crm.infrastructure.repository.CustomerActivityRepository;
import com.finotech.jewellery.modules.crm.infrastructure.repository.FollowUpRepository;
import com.finotech.jewellery.modules.customer.application.CustomerDirectory;
import com.finotech.jewellery.modules.loyalty.application.LoyaltyDirectory;
import com.finotech.jewellery.modules.sales.application.SalesHistory;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.security.SecurityUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRM behaviour: interactions, follow-ups and the Customer 360 view.
 *
 * <p>Kept separate from the customer master (section 17). The master holds who
 * the customer is; this module holds what has happened with them.
 */
@Service
@RequiredArgsConstructor
public class CrmService {

    private final CustomerActivityRepository activityRepository;
    private final FollowUpRepository followUpRepository;
    private final CustomerDirectory customerDirectory;
    private final SalesHistory salesHistory;
    private final LoyaltyDirectory loyaltyDirectory;
    private final AuditService auditService;

    // ---------- customer 360 ----------

    /**
     * Assembles one view of a customer from every module that knows something
     * about them, each through its published contract.
     */
    @Transactional(readOnly = true)
    public Customer360Response customer360(UUID customerId) {
        CustomerDirectory.CustomerView customer = customerDirectory.requireCustomer(customerId);
        SalesHistory.PurchaseSummary purchases = salesHistory.summaryFor(customerId);
        LoyaltyDirectory.AccountView loyalty = loyaltyDirectory.findAccount(customerId);

        Long daysSince = purchases.lastPurchaseDate() == null
                ? null
                : ChronoUnit.DAYS.between(purchases.lastPurchaseDate(), LocalDate.now());

        var purchaseStats = new Customer360Response.PurchaseStats(
                purchases.purchaseCount(), purchases.lifetimeSpend(),
                purchases.averageOrderValue(), purchases.firstPurchaseDate(),
                purchases.lastPurchaseDate(), daysSince);

        var loyaltyStanding = loyalty == null
                ? new Customer360Response.LoyaltyStanding(false, null, null, 0, 0, BigDecimal.ZERO)
                : new Customer360Response.LoyaltyStanding(true, loyalty.tierCode(),
                        loyalty.tierName(), loyalty.pointsBalance(), loyalty.lifetimePoints(),
                        loyalty.redeemableValue());

        List<ActivityResponse> recent = activityRepository
                .findTop20ByCustomerIdOrderByOccurredAtDesc(customerId).stream()
                .map(ActivityResponse::from).toList();
        List<FollowUpResponse> open = followUpRepository
                .findAllByCustomerIdAndStatusOrderByDueDateAsc(customerId, FollowUpStatus.OPEN)
                .stream().map(FollowUpResponse::from).toList();

        return new Customer360Response(customer.id(), customer.customerCode(), customer.fullName(),
                customer.phone(), customer.kycVerified(), purchaseStats, loyaltyStanding,
                recent, open);
    }

    // ---------- activities ----------

    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> searchActivities(UUID customerId, ActivityType type,
                                                           UUID branchId, Pageable pageable) {
        return PageResponse.of(activityRepository.search(customerId, type, branchId, pageable),
                ActivityResponse::from);
    }

    @Transactional
    public ActivityResponse logActivity(CrmRequests.ActivityRequest request) {
        customerDirectory.requireCustomer(request.customerId());

        CustomerActivity activity = new CustomerActivity();
        activity.setCustomerId(request.customerId());
        activity.setActivityType(request.activityType());
        activity.setSubject(request.subject().trim());
        activity.setDetails(request.details());
        activity.setBranchId(request.branchId());
        activity.setReferenceType(request.referenceType());
        activity.setReferenceId(request.referenceId());
        activity.setHandledBy(SecurityUtils.currentUsername().orElse("system"));
        activity.setOccurredAt(request.occurredAt() == null ? Instant.now() : request.occurredAt());

        return ActivityResponse.from(activityRepository.save(activity));
    }

    // ---------- follow-ups ----------

    @Transactional(readOnly = true)
    public PageResponse<FollowUpResponse> searchFollowUps(FollowUpStatus status, UUID customerId,
                                                          String assignedTo, UUID branchId,
                                                          LocalDate dueBefore, Pageable pageable) {
        return PageResponse.of(followUpRepository.search(status, customerId, assignedTo, branchId,
                dueBefore, pageable), FollowUpResponse::from);
    }

    /** The signed-in user's open follow-ups, due first. */
    @Transactional(readOnly = true)
    public PageResponse<FollowUpResponse> myFollowUps(Pageable pageable) {
        String me = SecurityUtils.requireCurrentUser().username();
        return PageResponse.of(followUpRepository.search(FollowUpStatus.OPEN, null, me, null,
                null, pageable), FollowUpResponse::from);
    }

    @Transactional
    public FollowUpResponse createFollowUp(CrmRequests.FollowUpRequest request) {
        customerDirectory.requireCustomer(request.customerId());

        FollowUp followUp = new FollowUp();
        followUp.setCustomerId(request.customerId());
        followUp.setTitle(request.title().trim());
        followUp.setDetails(request.details());
        followUp.setDueDate(request.dueDate());
        followUp.setAssignedTo(request.assignedTo().trim());
        followUp.setBranchId(request.branchId());
        followUp.setPriority(request.priority());
        followUp.setReferenceType(request.referenceType());
        followUp.setReferenceId(request.referenceId());
        followUp.setStatus(FollowUpStatus.OPEN);

        return FollowUpResponse.from(followUpRepository.save(followUp));
    }

    /**
     * Closes a follow-up and records what came of it. Completing also logs an
     * activity, so the customer timeline shows the contact happened.
     */
    @Transactional
    public FollowUpResponse completeFollowUp(UUID id, CrmRequests.CompleteFollowUpRequest request) {
        FollowUp followUp = requireFollowUp(id);
        if (followUp.getStatus() != FollowUpStatus.OPEN) {
            throw new ConflictException("Follow-up is " + followUp.getStatus()
                    + " and cannot be completed");
        }
        followUp.setStatus(FollowUpStatus.COMPLETED);
        followUp.setCompletedAt(Instant.now());
        followUp.setCompletedBy(SecurityUtils.currentUsername().orElse("system"));
        followUp.setOutcome(request.outcome());

        CustomerActivity activity = new CustomerActivity();
        activity.setCustomerId(followUp.getCustomerId());
        activity.setActivityType(ActivityType.NOTE);
        activity.setSubject("Follow-up completed: " + followUp.getTitle());
        activity.setDetails(request.outcome());
        activity.setBranchId(followUp.getBranchId());
        activity.setReferenceType("FollowUp");
        activity.setReferenceId(String.valueOf(followUp.getId()));
        activity.setHandledBy(followUp.getCompletedBy());
        activity.setOccurredAt(Instant.now());
        activityRepository.save(activity);

        auditService.record("FOLLOW_UP_COMPLETED", "FollowUp", id, null,
                Map.of("outcome", request.outcome()), followUp.getBranchId());
        return FollowUpResponse.from(followUp);
    }

    @Transactional
    public FollowUpResponse cancelFollowUp(UUID id, String reason) {
        FollowUp followUp = requireFollowUp(id);
        if (followUp.getStatus() != FollowUpStatus.OPEN) {
            throw new ConflictException("Only an open follow-up can be cancelled");
        }
        followUp.setStatus(FollowUpStatus.CANCELLED);
        followUp.setOutcome(reason);
        return FollowUpResponse.from(followUp);
    }

    private FollowUp requireFollowUp(UUID id) {
        return followUpRepository.findById(id).orElseThrow(() -> NotFoundException.of("FollowUp", id));
    }
}
