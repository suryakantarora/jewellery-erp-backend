package com.finotech.jewellery.modules.supplier.domain.enums;

public enum SupplierStatus {
    ACTIVE,
    INACTIVE,
    /** Trading suspended pending review; no new purchase orders allowed. */
    BLOCKED
}
