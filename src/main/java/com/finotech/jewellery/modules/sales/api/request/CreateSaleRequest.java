package com.finotech.jewellery.modules.sales.api.request;

import com.finotech.jewellery.modules.pricing.domain.enums.DiscountType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Opens a sale. Each line is priced by the pricing engine at this moment and
 * the result is frozen onto the sale.
 *
 * @param discountRequestId optional approved discount request that authorises
 *                          the line discounts on this sale, for callers who
 *                          do not hold DISCOUNT_APPROVE themselves
 */
public record CreateSaleRequest(@NotNull UUID customerId,
                                @NotNull UUID branchId,
                                UUID locationId,
                                UUID quotationId,
                                UUID salespersonId,
                                @DecimalMin("0.0") BigDecimal exchangeCredit,
                                /** Loyalty points to spend against this sale. */
                                @PositiveOrZero Long redeemPoints,
                                @Size(max = 500) String notes,
                                @NotEmpty @Valid List<Line> lines,
                                UUID discountRequestId) {

    /** Shape before discount requests existed; kept for existing callers. */
    public CreateSaleRequest(UUID customerId, UUID branchId, UUID locationId, UUID quotationId,
                             UUID salespersonId, BigDecimal exchangeCredit, Long redeemPoints,
                             String notes, List<Line> lines) {
        this(customerId, branchId, locationId, quotationId, salespersonId, exchangeCredit,
                redeemPoints, notes, lines, null);
    }

    public record Line(@NotNull UUID jewelleryItemId,
                       DiscountType discountType,
                       @DecimalMin("0.0") BigDecimal discountValue) {
    }
}
