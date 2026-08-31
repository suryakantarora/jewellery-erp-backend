package com.finotech.jewellery.modules.product.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published contract for other modules (Inventory, Pricing, Sales) to read
 * product master data without touching product tables.
 */
public interface ProductCatalog {

    ProductView requireProduct(UUID productId);

    record ProductView(UUID id,
                       String sku,
                       String name,
                       UUID productTypeId,
                       UUID defaultMetalId,
                       UUID defaultPurityId,
                       BigDecimal defaultMakingChargeValue,
                       String defaultMakingChargeType,
                       BigDecimal defaultWastagePercentage,
                       boolean active) {
    }
}
