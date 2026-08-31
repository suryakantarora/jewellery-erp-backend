package com.finotech.jewellery.modules.loyalty.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The standing benefits a customer's loyalty tier grants.
 *
 * <p>Separate from {@link LoyaltyDirectory} because Pricing needs only this much
 * and should not be able to reach point balances or move them.
 */
public interface LoyaltyTierBenefits {

    /**
     * @return the tier entitlement, or {@code null} when the customer is not
     *         enrolled or their tier grants no discount
     */
    TierBenefit benefitFor(UUID customerId);

    record TierBenefit(String tierCode, String tierName, BigDecimal discountPercentage) {
    }
}
