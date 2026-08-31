package com.finotech.jewellery.modules.inventory.domain.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle states of a physical jewellery item, with the transitions the
 * platform permits. Centralising the rules here is what stops a sold item from
 * being transferred or an item under repair from being sold (section 12).
 */
public enum ItemStatus {

    /** Created but not yet quality-checked or tagged. */
    DRAFT,
    AVAILABLE,
    RESERVED,
    IN_TRANSIT,
    SOLD,
    UNDER_REPAIR,
    RETURNED,
    EXCHANGED,
    BUYBACK,
    SCRAPPED;

    private static final Map<ItemStatus, Set<ItemStatus>> ALLOWED = Map.of(
            DRAFT, EnumSet.of(AVAILABLE, SCRAPPED),
            AVAILABLE, EnumSet.of(RESERVED, IN_TRANSIT, SOLD, UNDER_REPAIR, SCRAPPED),
            RESERVED, EnumSet.of(AVAILABLE, SOLD, IN_TRANSIT),
            IN_TRANSIT, EnumSet.of(AVAILABLE, RESERVED),
            SOLD, EnumSet.of(RETURNED, EXCHANGED, UNDER_REPAIR, BUYBACK),
            UNDER_REPAIR, EnumSet.of(AVAILABLE, SOLD, SCRAPPED),
            RETURNED, EnumSet.of(AVAILABLE, SCRAPPED),
            EXCHANGED, EnumSet.of(AVAILABLE, SCRAPPED),
            BUYBACK, EnumSet.of(AVAILABLE, SCRAPPED),
            SCRAPPED, EnumSet.noneOf(ItemStatus.class));

    public boolean canTransitionTo(ItemStatus target) {
        return this != target && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public Set<ItemStatus> allowedTransitions() {
        return ALLOWED.getOrDefault(this, Set.of());
    }

    /** Statuses in which the item is physically present and sellable stock. */
    public boolean isInStock() {
        return this == AVAILABLE || this == RESERVED;
    }

    /** A terminal state cannot change again. */
    public boolean isTerminal() {
        return this == SCRAPPED;
    }
}
