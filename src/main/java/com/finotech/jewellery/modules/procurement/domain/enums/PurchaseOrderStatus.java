package com.finotech.jewellery.modules.procurement.domain.enums;

public enum PurchaseOrderStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELLED,
    CLOSED;

    /** Goods may only be booked in against an order in one of these states. */
    public boolean acceptsReceipt() {
        return this == APPROVED || this == PARTIALLY_RECEIVED;
    }
}
