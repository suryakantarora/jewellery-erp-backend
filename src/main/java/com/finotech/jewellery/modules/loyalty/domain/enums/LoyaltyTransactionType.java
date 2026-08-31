package com.finotech.jewellery.modules.loyalty.domain.enums;

/**
 * Why a customer's point balance changed. Every movement is recorded rather
 * than the balance simply being overwritten, so a disputed balance can always
 * be reconstructed.
 */
public enum LoyaltyTransactionType {
    /** Points awarded for a purchase. */
    EARN,
    /** Points spent by the customer. */
    REDEEM,
    /** Points reversed because the purchase was returned. */
    REVERSAL,
    /** Points removed because they aged out. */
    EXPIRY,
    /** Manual correction; always requires a reason and is audited. */
    ADJUSTMENT;

    /** Whether this type adds to the balance. */
    public boolean isCredit() {
        return this == EARN;
    }
}
