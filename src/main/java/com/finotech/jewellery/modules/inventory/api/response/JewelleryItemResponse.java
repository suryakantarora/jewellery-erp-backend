package com.finotech.jewellery.modules.inventory.api.response;

import com.finotech.jewellery.modules.inventory.domain.entity.ItemImage;
import com.finotech.jewellery.modules.inventory.domain.entity.JewelleryItem;
import com.finotech.jewellery.modules.inventory.domain.enums.ItemStatus;
import com.finotech.jewellery.modules.product.application.ProductCatalog;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record JewelleryItemResponse(UUID id, String itemCode, UUID productId, UUID designId,
                                    UUID metalId, UUID purityId, BigDecimal grossWeight,
                                    BigDecimal netMetalWeight, BigDecimal stoneWeight,
                                    int stoneCount, BigDecimal totalCarat, UUID sizeId,
                                    String rfidTag, String qrCode, String barcode,
                                    String hallmarkNumber, BigDecimal purchaseCost,
                                    BigDecimal makingCost, BigDecimal stoneCost,
                                    BigDecimal totalCost, BigDecimal currentPrice, String currency,
                                    ItemStatus status, Set<ItemStatus> allowedTransitions,
                                    UUID currentLocationId, UUID currentBranchId, UUID binId,
                                    String primaryImageKey,
                                    UUID reservedForCustomerId, Instant reservedUntil,
                                    UUID supplierId, LocalDate receivedDate, LocalDate soldDate,
                                    UUID ownerCustomerId, boolean qualityChecked, String notes,
                                    long version,
                                    String productName, String productCode, String designName,
                                    String metalName, String purityCode,
                                    String currentLocationName, String currentBranchName,
                                    String binCode, String supplierName) {

    /** The item alone; every display name is null. Used for audit snapshots. */
    public static JewelleryItemResponse from(JewelleryItem i) {
        return from(i, DisplayNames.EMPTY);
    }

    /**
     * The item with its references labelled from batch-resolved maps, so a
     * page of rows costs one lookup per reference type rather than per row.
     */
    public static JewelleryItemResponse from(JewelleryItem i, DisplayNames names) {
        ProductCatalog.ProductLabel product = names.products().get(i.getProductId());
        return new JewelleryItemResponse(i.getId(), i.getItemCode(), i.getProductId(), i.getDesignId(),
                i.getMetalId(), i.getPurityId(), i.getGrossWeight(), i.getNetMetalWeight(),
                i.getStoneWeight(), i.getStoneCount(), i.getTotalCarat(), i.getSizeId(),
                i.getRfidTag(), i.getQrCode(), i.getBarcode(), i.getHallmarkNumber(),
                i.getPurchaseCost(), i.getMakingCost(), i.getStoneCost(), i.getTotalCost(),
                i.getCurrentPrice(), i.getCurrency(), i.getStatus(),
                i.getStatus().allowedTransitions(), i.getCurrentLocationId(), i.getCurrentBranchId(), i.getBinId(),
                primaryImageKey(i),
                i.getReservedForCustomerId(), i.getReservedUntil(), i.getSupplierId(),
                i.getReceivedDate(), i.getSoldDate(), i.getOwnerCustomerId(), i.isQualityChecked(),
                i.getNotes(), i.getVersion(),
                product == null ? null : product.name(),
                product == null ? null : product.sku(),
                lookup(names.designs(), i.getDesignId()),
                lookup(names.metals(), i.getMetalId()),
                lookup(names.purities(), i.getPurityId()),
                lookup(names.locations(), i.getCurrentLocationId()),
                lookup(names.branches(), i.getCurrentBranchId()),
                lookup(names.bins(), i.getBinId()),
                lookup(names.suppliers(), i.getSupplierId()));
    }

    /**
     * The one image a list row or passport header should show.
     *
     * <p>Returned inline so rendering a page of results costs no extra request
     * per item — the whole reason the reference cache exists is that the same
     * mistake was expensive elsewhere.
     */
    private static String primaryImageKey(JewelleryItem i) {
        return i.getImages().stream()
                .filter(ItemImage::isPrimaryImage)
                .map(ItemImage::getStorageKey)
                .findFirst()
                .orElse(null);
    }

    private static String lookup(Map<UUID, String> map, UUID id) {
        return id == null ? null : map.get(id);
    }

    /**
     * Labels for every reference an item carries, keyed by id. Built once per
     * page or list by the service; a missing key simply yields a null name.
     */
    public record DisplayNames(Map<UUID, ProductCatalog.ProductLabel> products,
                               Map<UUID, String> designs,
                               Map<UUID, String> metals,
                               Map<UUID, String> purities,
                               Map<UUID, String> locations,
                               Map<UUID, String> branches,
                               Map<UUID, String> bins,
                               Map<UUID, String> suppliers) {

        public static final DisplayNames EMPTY = new DisplayNames(Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }
}
