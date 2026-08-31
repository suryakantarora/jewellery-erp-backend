package com.finotech.jewellery.modules.sales.domain.enums;

/**
 * Sale lifecycle. A sale is only CONFIRMED once it is fully paid, which is the
 * point at which items are marked SOLD and leave stock.
 */
public enum SaleStatus {
    /** Being built at the counter; nothing has left stock. */
    DRAFT,
    /** Awaiting payment; the items are reserved for this sale. */
    PENDING_PAYMENT,
    CONFIRMED,
    DELIVERED,
    PARTIALLY_RETURNED,
    RETURNED,
    CANCELLED;

    public boolean isOpen() {
        return this == DRAFT || this == PENDING_PAYMENT;
    }

    public boolean isSettled() {
        return this == CONFIRMED || this == DELIVERED
                || this == PARTIALLY_RETURNED || this == RETURNED;
    }
}
