package com.finotech.jewellery.modules.sales.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The seam Payment uses to report money against a sale.
 *
 * <p>Payment does not decide when goods leave stock; it reports what has been
 * collected and Sales decides. This keeps dependency rule 4 intact — payment
 * never changes inventory.
 */
public interface SaleSettlement {

    /** What the sale still expects, so a payment cannot exceed it. */
    SaleBalance balanceOf(UUID saleId);

    /**
     * Applies a captured amount. When the sale becomes fully paid it is
     * confirmed, which is what marks its items SOLD.
     *
     * @param amount positive for a payment, negative for a refund
     */
    void applyPayment(UUID saleId, BigDecimal amount);

    record SaleBalance(UUID saleId, UUID customerId, UUID branchId, String currency,
                       BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal outstandingAmount,
                       String status, boolean acceptsPayment) {
    }
}
