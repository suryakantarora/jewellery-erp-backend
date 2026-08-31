package com.finotech.jewellery.modules.repair.domain.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Repair workflow (section 16). The customer-approval gate is deliberate: no
 * chargeable work starts until the customer has accepted the estimate.
 */
public enum RepairStatus {
    RECEIVED,
    INSPECTION,
    ESTIMATION,
    APPROVAL_PENDING,
    /** The customer declined the estimate; the piece is returned unrepaired. */
    DECLINED,
    IN_PROGRESS,
    QUALITY_CHECK,
    READY,
    DELIVERED,
    CANCELLED;

    private static final Map<RepairStatus, Set<RepairStatus>> ALLOWED = Map.of(
            RECEIVED, EnumSet.of(INSPECTION, CANCELLED),
            INSPECTION, EnumSet.of(ESTIMATION, CANCELLED),
            ESTIMATION, EnumSet.of(APPROVAL_PENDING, CANCELLED),
            APPROVAL_PENDING, EnumSet.of(IN_PROGRESS, DECLINED, CANCELLED),
            DECLINED, EnumSet.of(DELIVERED),
            IN_PROGRESS, EnumSet.of(QUALITY_CHECK, CANCELLED),
            QUALITY_CHECK, EnumSet.of(READY, IN_PROGRESS),
            READY, EnumSet.of(DELIVERED),
            DELIVERED, EnumSet.noneOf(RepairStatus.class),
            CANCELLED, EnumSet.noneOf(RepairStatus.class));

    public boolean canTransitionTo(RepairStatus target) {
        return this != target && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public Set<RepairStatus> allowedTransitions() {
        return ALLOWED.getOrDefault(this, Set.of());
    }

    /** Whether the piece is still physically with the business. */
    public boolean isInWorkshop() {
        return this != DELIVERED && this != CANCELLED;
    }
}
