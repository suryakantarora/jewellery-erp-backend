package com.finotech.jewellery.modules.loyalty.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published contract for CRM and Sales to read a customer's loyalty standing.
 */
public interface LoyaltyDirectory {

    /** Null-safe: a customer never enrolled simply has no account. */
    AccountView findAccount(UUID customerId);

    record AccountView(UUID accountId, UUID customerId, String tierCode, String tierName,
                       long pointsBalance, long lifetimePoints, BigDecimal redeemableValue) {
    }
}
