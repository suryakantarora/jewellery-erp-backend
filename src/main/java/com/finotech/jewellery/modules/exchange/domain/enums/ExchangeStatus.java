package com.finotech.jewellery.modules.exchange.domain.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Intake workflow (section 15): weight check → purity check → valuation →
 * approval → completion. Each step must happen in order, because the valuation
 * is only defensible if the weight and purity behind it were recorded first.
 */
public enum ExchangeStatus {
    RECEIVED,
    WEIGHED,
    PURITY_TESTED,
    VALUED,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    COMPLETED,
    /** The customer declined the offer and took the piece back. */
    RETURNED_TO_CUSTOMER,
    CANCELLED;

    private static final Map<ExchangeStatus, Set<ExchangeStatus>> ALLOWED = Map.of(
            RECEIVED, EnumSet.of(WEIGHED, RETURNED_TO_CUSTOMER, CANCELLED),
            WEIGHED, EnumSet.of(PURITY_TESTED, RETURNED_TO_CUSTOMER, CANCELLED),
            PURITY_TESTED, EnumSet.of(VALUED, RETURNED_TO_CUSTOMER, CANCELLED),
            VALUED, EnumSet.of(PENDING_APPROVAL, RETURNED_TO_CUSTOMER, CANCELLED),
            PENDING_APPROVAL, EnumSet.of(APPROVED, REJECTED),
            APPROVED, EnumSet.of(COMPLETED, RETURNED_TO_CUSTOMER),
            REJECTED, EnumSet.of(RETURNED_TO_CUSTOMER),
            COMPLETED, EnumSet.noneOf(ExchangeStatus.class),
            RETURNED_TO_CUSTOMER, EnumSet.noneOf(ExchangeStatus.class),
            CANCELLED, EnumSet.noneOf(ExchangeStatus.class));

    public boolean canTransitionTo(ExchangeStatus target) {
        return this != target && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public Set<ExchangeStatus> allowedTransitions() {
        return ALLOWED.getOrDefault(this, Set.of());
    }
}
