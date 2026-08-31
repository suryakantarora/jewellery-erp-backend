package com.finotech.jewellery.modules.inventory.domain.enums;

public enum MovementType {

    /** Stock entering the platform from procurement or manufacturing. */
    GOODS_RECEIPT,
    TRANSFER,
    ISSUE,
    RETURN,
    /** Correction after a stock count. */
    ADJUSTMENT,
    SALE_DELIVERY,
    SALE_RETURN,
    REPAIR_OUT,
    REPAIR_IN,
    SCRAP
}
