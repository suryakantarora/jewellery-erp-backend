package com.finotech.jewellery.modules.sales.domain.enums;

/**
 * Life of a discount request: raised, decided, then either spent on a sale or
 * left to lapse. An approval is time-boxed because metal rates move.
 */
public enum DiscountRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** Applied to a sale; cannot be used again. */
    CONSUMED,
    EXPIRED,
    CANCELLED;

    public boolean isOpen() {
        return this == PENDING || this == APPROVED;
    }
}
