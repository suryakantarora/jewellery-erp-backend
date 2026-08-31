package com.finotech.jewellery.modules.inventory.domain.enums;

/**
 * Movement workflow. A movement only changes an item's location when it is
 * completed; while dispatched, the item sits in IN_TRANSIT.
 */
public enum MovementStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    DISPATCHED,
    COMPLETED,
    CANCELLED
}
