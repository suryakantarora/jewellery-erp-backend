package com.finotech.jewellery.modules.loyalty.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class LoyaltyRequests {

    private LoyaltyRequests() {
    }

    public record ProgramRequest(@NotBlank @Size(max = 40) String code,
                                 @NotBlank @Size(max = 150) String name,
                                 UUID companyId,
                                 @NotNull @DecimalMin(value = "0.0", inclusive = false)
                                 BigDecimal pointsPerCurrencyUnit,
                                 @NotNull @DecimalMin(value = "0.0", inclusive = false)
                                 BigDecimal currencyValuePerPoint,
                                 @Positive Integer pointsValidityMonths,
                                 @PositiveOrZero Integer minimumRedeemablePoints,
                                 boolean earnOnMakingChargeOnly,
                                 @NotNull LocalDate effectiveFrom,
                                 LocalDate effectiveTo,
                                 @NotEmpty @Valid List<TierRequest> tiers) {
    }

    public record TierRequest(@NotBlank @Size(max = 30) String code,
                              @NotBlank @Size(max = 100) String name,
                              @PositiveOrZero long minimumPoints,
                              @NotNull @DecimalMin("1.0") BigDecimal earnMultiplier,
                              @DecimalMin("0.0") BigDecimal discountPercentage,
                              Integer displayOrder,
                              @Size(max = 1000) String benefits) {
    }

    public record EnrolRequest(@NotNull UUID customerId) {
    }

    public record RedeemRequest(@NotNull UUID customerId,
                                @Positive long points,
                                UUID saleId,
                                UUID branchId,
                                @Size(max = 500) String reason) {
    }

    public record AdjustRequest(@NotNull UUID customerId,
                                long points,
                                @NotBlank @Size(max = 500) String reason) {
    }
}
