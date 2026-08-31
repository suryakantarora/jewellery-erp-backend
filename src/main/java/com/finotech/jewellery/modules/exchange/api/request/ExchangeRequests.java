package com.finotech.jewellery.modules.exchange.api.request;

import com.finotech.jewellery.modules.exchange.domain.enums.ExchangeType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Requests for each step of the intake workflow. */
public final class ExchangeRequests {

    private ExchangeRequests() {
    }

    public record ReceiveRequest(@NotNull ExchangeType exchangeType,
                                 @NotNull UUID customerId,
                                 @NotNull UUID branchId,
                                 UUID locationId,
                                 @NotNull UUID metalId,
                                 UUID declaredPurityId,
                                 UUID originalItemId,
                                 @NotBlank @Size(max = 500) String description,
                                 @Positive int itemCount,
                                 @Size(max = 500) String notes) {
    }

    public record WeighRequest(@NotNull @DecimalMin(value = "0.0", inclusive = false)
                               BigDecimal grossWeight,
                               @DecimalMin("0.0") BigDecimal stoneWeight) {
    }

    public record PurityTestRequest(@NotNull UUID testedPurityId,
                                    @Size(max = 50) String testMethod) {
    }

    /**
     * @param deductionPercentage refining loss or wear, deducted from the gross
     *                            valuation before the offer is made
     */
    public record ValuationRequest(@DecimalMin("0.0") @DecimalMax("100.0")
                                   BigDecimal deductionPercentage,
                                   @Size(max = 500) String notes) {
    }

    public record CompleteRequest(UUID appliedSaleId,
                                  UUID scrapLocationId,
                                  @Size(max = 500) String notes) {
    }
}
