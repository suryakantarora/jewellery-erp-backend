package com.finotech.jewellery.modules.warehouse.domain.enums;

/**
 * Storage granularity inside a location (section 19). A location is where stock
 * is; a bin is exactly where in that location to find it.
 */
public enum BinType {
    ZONE,
    SHELF,
    TRAY,
    BIN,
    SAFE
}
