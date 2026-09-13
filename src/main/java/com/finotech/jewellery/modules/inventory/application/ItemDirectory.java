package com.finotech.jewellery.modules.inventory.application;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only lookups of item display data for other modules' list rows
 * (wishlists, approvals). Kept apart from {@link InventoryOperations}, which
 * is the contract for changing item state.
 */
public interface ItemDirectory {

    /** Summaries keyed by item id; ids that do not exist are simply absent. */
    Map<UUID, ItemSummary> summariesFor(Collection<UUID> itemIds);

    record ItemSummary(UUID id,
                       String itemCode,
                       UUID productId,
                       UUID designId,
                       BigDecimal currentPrice,
                       String currency,
                       String status,
                       UUID currentBranchId,
                       String primaryImageKey) {
    }
}
