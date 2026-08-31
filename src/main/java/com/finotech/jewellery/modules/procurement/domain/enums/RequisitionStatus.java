package com.finotech.jewellery.modules.procurement.domain.enums;

public enum RequisitionStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    /** A purchase order has been raised against this requisition. */
    ORDERED,
    CANCELLED
}
