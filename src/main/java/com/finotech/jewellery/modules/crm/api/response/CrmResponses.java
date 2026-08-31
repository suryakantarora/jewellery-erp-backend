package com.finotech.jewellery.modules.crm.api.response;

import com.finotech.jewellery.modules.crm.domain.entity.Campaign;
import com.finotech.jewellery.modules.crm.domain.entity.CustomerActivity;
import com.finotech.jewellery.modules.crm.domain.entity.CustomerSegment;
import com.finotech.jewellery.modules.crm.domain.entity.FollowUp;
import com.finotech.jewellery.modules.crm.domain.enums.ActivityType;
import com.finotech.jewellery.modules.crm.domain.enums.CampaignStatus;
import com.finotech.jewellery.modules.crm.domain.enums.FollowUpStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CrmResponses {

    private CrmResponses() {
    }

    public record ActivityResponse(UUID id, UUID customerId, ActivityType activityType,
                                   String subject, String details, UUID branchId, String handledBy,
                                   String referenceType, String referenceId, Instant occurredAt) {

        public static ActivityResponse from(CustomerActivity a) {
            return new ActivityResponse(a.getId(), a.getCustomerId(), a.getActivityType(),
                    a.getSubject(), a.getDetails(), a.getBranchId(), a.getHandledBy(),
                    a.getReferenceType(), a.getReferenceId(), a.getOccurredAt());
        }
    }

    public record FollowUpResponse(UUID id, UUID customerId, String title, String details,
                                   LocalDate dueDate, String assignedTo, UUID branchId,
                                   FollowUpStatus status, String priority, boolean overdue,
                                   String referenceType, String referenceId, Instant completedAt,
                                   String completedBy, String outcome) {

        public static FollowUpResponse from(FollowUp f) {
            return new FollowUpResponse(f.getId(), f.getCustomerId(), f.getTitle(), f.getDetails(),
                    f.getDueDate(), f.getAssignedTo(), f.getBranchId(), f.getStatus(),
                    f.getPriority(), f.isOverdue(), f.getReferenceType(), f.getReferenceId(),
                    f.getCompletedAt(), f.getCompletedBy(), f.getOutcome());
        }
    }

    public record SegmentResponse(UUID id, String code, String name, String description,
                                  UUID branchId, String tierCode, BigDecimal minLifetimeSpend,
                                  Integer minPurchaseCount, Integer inactiveDays,
                                  Integer birthdayMonth, boolean active) {

        public static SegmentResponse from(CustomerSegment s) {
            return new SegmentResponse(s.getId(), s.getCode(), s.getName(), s.getDescription(),
                    s.getBranchId(), s.getTierCode(), s.getMinLifetimeSpend(),
                    s.getMinPurchaseCount(), s.getInactiveDays(), s.getBirthdayMonth(),
                    s.isActive());
        }
    }

    public record CampaignResponse(UUID id, String code, String name, String description,
                                   CampaignStatus status, UUID segmentId, String templateCode,
                                   UUID branchId, LocalDate startsOn, LocalDate endsOn,
                                   int targetCount, int sentCount, Instant launchedAt,
                                   Instant completedAt) {

        public static CampaignResponse from(Campaign c) {
            return new CampaignResponse(c.getId(), c.getCode(), c.getName(), c.getDescription(),
                    c.getStatus(), c.getSegmentId(), c.getTemplateCode(), c.getBranchId(),
                    c.getStartsOn(), c.getEndsOn(), c.getTargetCount(), c.getSentCount(),
                    c.getLaunchedAt(), c.getCompletedAt());
        }
    }

    /**
     * Customer 360: identity, what they have bought, their loyalty standing, and
     * recent contact — assembled from each owning module rather than from a
     * single joined query.
     */
    public record Customer360Response(UUID customerId, String customerCode, String fullName,
                                      String phone, boolean kycVerified, PurchaseStats purchases,
                                      LoyaltyStanding loyalty, List<ActivityResponse> recentActivity,
                                      List<FollowUpResponse> openFollowUps) {

        public record PurchaseStats(long purchaseCount, BigDecimal lifetimeSpend,
                                    BigDecimal averageOrderValue, LocalDate firstPurchaseDate,
                                    LocalDate lastPurchaseDate, Long daysSinceLastPurchase) {
        }

        public record LoyaltyStanding(boolean enrolled, String tierCode, String tierName,
                                      long pointsBalance, long lifetimePoints,
                                      BigDecimal redeemableValue) {
        }
    }
}
