package com.finotech.jewellery.modules.sales.api.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Requests for the discount approval workflow. */
public final class DiscountRequestRequests {

    private DiscountRequestRequests() {
    }

    /**
     * Exactly one of {@code requestedPercentage} / {@code requestedAmount}.
     *
     * @param jewelleryItemId optional; when set the approval only covers that item
     * @param customerId      optional; when set the approval only covers that customer
     */
    public record CreateDiscountRequest(@NotNull UUID branchId,
                                        UUID customerId,
                                        UUID jewelleryItemId,
                                        UUID quotationId,
                                        @DecimalMin(value = "0.0", inclusive = false)
                                        @DecimalMax("100.0") BigDecimal requestedPercentage,
                                        @DecimalMin(value = "0.0", inclusive = false)
                                        BigDecimal requestedAmount,
                                        @Size(max = 3) String currency,
                                        @NotBlank @Size(max = 500) String reason) {
    }

    public record DecisionNoteRequest(@Size(max = 500) String reason) {
    }
}
