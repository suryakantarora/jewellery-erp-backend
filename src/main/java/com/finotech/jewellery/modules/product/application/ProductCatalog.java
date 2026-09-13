package com.finotech.jewellery.modules.product.application;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Published contract for other modules (Inventory, Pricing, Sales) to read
 * product master data without touching product tables.
 */
public interface ProductCatalog {

    ProductView requireProduct(UUID productId);

    /**
     * SKU and name for a batch of products, so a page of inventory rows can be
     * labelled with one query. Unknown ids are absent from the result.
     */
    Map<UUID, ProductLabel> labelsFor(Collection<UUID> productIds);

    /** Design names for a batch of ids; unknown ids are absent. */
    Map<UUID, String> designNamesFor(Collection<UUID> designIds);

    record ProductLabel(UUID id, String sku, String name) {
    }

    record ProductView(UUID id,
                       String sku,
                       String name,
                       UUID designId,
                       UUID productTypeId,
                       UUID defaultMetalId,
                       UUID defaultPurityId,
                       BigDecimal defaultMakingChargeValue,
                       String defaultMakingChargeType,
                       BigDecimal defaultWastagePercentage,
                       boolean active) {
    }
}
