package com.finotech.jewellery.modules.sales.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Published contract for CRM, Loyalty and Reporting to read what a customer has
 * bought, without querying sales tables directly.
 */
public interface SalesHistory {

    PurchaseSummary summaryFor(UUID customerId);

    /**
     * Batch form. Segment evaluation uses this so a segment over thousands of
     * customers is one query rather than one per customer.
     */
    Map<UUID, PurchaseSummary> summariesFor(Collection<UUID> customerIds);

    record PurchaseSummary(UUID customerId,
                           long purchaseCount,
                           BigDecimal lifetimeSpend,
                           BigDecimal averageOrderValue,
                           LocalDate firstPurchaseDate,
                           LocalDate lastPurchaseDate) {

        public static PurchaseSummary empty(UUID customerId) {
            return new PurchaseSummary(customerId, 0, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        }
    }
}
