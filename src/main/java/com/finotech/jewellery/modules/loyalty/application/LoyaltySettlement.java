package com.finotech.jewellery.modules.loyalty.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The seam Sales uses to spend loyalty points against a sale.
 *
 * <p>Deliberately narrow: Sales can spend points for a specific sale and hand
 * them back if that sale is abandoned, and nothing else. It cannot award,
 * adjust or read balances.
 */
public interface LoyaltySettlement {

    /**
     * Spends points and returns the money they are worth.
     *
     * @throws com.finotech.jewellery.shared.exception.ValidationException if the
     *         balance is insufficient or below the program minimum
     */
    Redemption redeemForSale(UUID customerId, long points, UUID saleId, UUID branchId);

    /**
     * Returns points spent on a sale that was cancelled or returned. Safe to
     * call when nothing was redeemed.
     *
     * @return the points handed back
     */
    long refundRedemptionForSale(UUID saleId);

    record Redemption(long points, BigDecimal value) {
    }
}
