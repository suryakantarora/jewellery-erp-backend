package com.finotech.jewellery.modules.crm.api.request;

import com.finotech.jewellery.modules.crm.domain.enums.ActivityType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class CrmRequests {

    private CrmRequests() {
    }

    public record ActivityRequest(@NotNull UUID customerId,
                                  @NotNull ActivityType activityType,
                                  @NotBlank @Size(max = 200) String subject,
                                  String details,
                                  UUID branchId,
                                  @Size(max = 50) String referenceType,
                                  @Size(max = 100) String referenceId,
                                  Instant occurredAt) {
    }

    public record FollowUpRequest(@NotNull UUID customerId,
                                  @NotBlank @Size(max = 200) String title,
                                  @Size(max = 1000) String details,
                                  @NotNull LocalDate dueDate,
                                  @NotBlank @Size(max = 100) String assignedTo,
                                  UUID branchId,
                                  @Size(max = 20) String priority,
                                  @Size(max = 50) String referenceType,
                                  @Size(max = 100) String referenceId) {
    }

    public record CompleteFollowUpRequest(@NotBlank @Size(max = 1000) String outcome) {
    }

    public record SegmentRequest(@NotBlank @Size(max = 40) String code,
                                 @NotBlank @Size(max = 150) String name,
                                 @Size(max = 500) String description,
                                 UUID branchId,
                                 @Size(max = 30) String tierCode,
                                 @DecimalMin("0.0") BigDecimal minLifetimeSpend,
                                 @PositiveOrZero Integer minPurchaseCount,
                                 @PositiveOrZero Integer inactiveDays,
                                 @Min(1) @Max(12) Integer birthdayMonth) {
    }

    public record CampaignRequest(@NotBlank @Size(max = 40) String code,
                                  @NotBlank @Size(max = 150) String name,
                                  @Size(max = 1000) String description,
                                  UUID segmentId,
                                  @Size(max = 60) String templateCode,
                                  UUID branchId,
                                  LocalDate startsOn,
                                  LocalDate endsOn) {
    }
}
