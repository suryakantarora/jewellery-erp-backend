package com.finotech.jewellery.modules.inventory.api.response;

import com.finotech.jewellery.modules.inventory.domain.entity.ItemImage;
import com.finotech.jewellery.modules.inventory.domain.entity.JewelleryItem;
import com.finotech.jewellery.modules.inventory.domain.enums.ItemStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
                                    long version) {

    public static JewelleryItemResponse from(JewelleryItem i) {
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
                i.getNotes(), i.getVersion());
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
}
