package com.finotech.jewellery.modules.procurement.domain.enums;

public enum GoodsReceiptStatus {
    DRAFT,
    /** Physically counted and weighed, awaiting quality check. */
    PENDING_QUALITY_CHECK,
    /** Accepted: serialized items have been created in inventory. */
    ACCEPTED,
    REJECTED,
    CANCELLED
}
