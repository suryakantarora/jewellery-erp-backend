package com.finotech.jewellery.modules.inventory.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The only way other modules may change item state.
 *
 * <p>Sales calls {@link #markSold} rather than writing the item row itself
 * (dependency rules 2 and 3); Payment never touches inventory at all (rule 4).
 */
public interface InventoryOperations {

    ItemView requireItem(UUID itemId);

    /**
     * Brings one physical piece into stock, as a goods receipt does. The item is
     * created in DRAFT: it still has to be tagged and quality-checked before it
     * can be sold, which is what {@code releaseToStock} does.
     *
     * @return the id of the created item
     */
    UUID intake(ItemIntake intake);

    /**
     * Every item the platform believes is physically at a location. This is the
     * expected sheet a stock verification counts against.
     */
    java.util.List<ItemView> itemsAtLocation(UUID locationId);

    /** Holds an item for a customer. Fails if it is already reserved or not available. */
    void reserve(UUID itemId, UUID customerId, int holdHours);

    void releaseReservation(UUID itemId);

    /**
     * Marks an item sold and transfers ownership.
     *
     * @throws com.finotech.jewellery.shared.exception.ConflictException if the item
     *         is not sellable, or is reserved for a different customer
     */
    void markSold(UUID itemId, UUID customerId, UUID saleId, BigDecimal salePrice);

    /** Reverses a sale, returning the item to stock at the given location. */
    void markReturned(UUID itemId, UUID saleId, UUID returnToLocationId, String reason);

    /** A single piece being brought into stock from procurement or manufacturing. */
    record ItemIntake(UUID productId,
                      UUID metalId,
                      UUID purityId,
                      BigDecimal grossWeight,
                      BigDecimal stoneWeight,
                      BigDecimal purchaseCost,
                      BigDecimal makingCost,
                      BigDecimal stoneCost,
                      UUID supplierId,
                      UUID locationId,
                      String barcode,
                      String rfidTag,
                      String hallmarkNumber,
                      String sourceType,
                      String sourceReference,
                      String notes) {
    }

    record ItemView(UUID id,
                    String itemCode,
                    UUID productId,
                    UUID metalId,
                    UUID purityId,
                    BigDecimal grossWeight,
                    BigDecimal netMetalWeight,
                    BigDecimal stoneWeight,
                    BigDecimal totalCarat,
                    /** Recorded value of the stones set in this item. */
                    BigDecimal stoneValue,
                    /** What the item cost the business; the basis of cost of goods sold. */
                    BigDecimal totalCost,
                    BigDecimal currentPrice,
                    String status,
                    UUID currentLocationId,
                    UUID currentBranchId,
                    boolean sellable) {
    }
}
