package com.finotech.jewellery.modules.inventory.domain.enums;

/**
 * Entries in the digital passport of an item (section 8).
 */
public enum LifecycleEventType {
    CREATED,
    QUALITY_CHECKED,
    TAGGED,
    STATUS_CHANGED,
    LOCATION_CHANGED,
    RESERVED,
    RESERVATION_RELEASED,
    PRICE_UPDATED,
    SOLD,
    RETURNED,
    REPAIRED,
    EXCHANGED,
    BOUGHT_BACK,
    SCRAPPED
}
