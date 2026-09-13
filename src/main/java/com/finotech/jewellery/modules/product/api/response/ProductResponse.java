package com.finotech.jewellery.modules.product.api.response;

import com.finotech.jewellery.modules.product.domain.entity.Product;
import com.finotech.jewellery.modules.product.domain.entity.ProductImage;
import com.finotech.jewellery.modules.product.domain.enums.MasterStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(UUID id, String sku, String name, UUID designId, UUID productTypeId,
                              UUID categoryId, UUID brandId, UUID collectionId, UUID defaultMetalId,
                              UUID defaultPurityId, BigDecimal nominalGrossWeight,
                              String defaultMakingChargeType, BigDecimal defaultMakingChargeValue,
                              BigDecimal defaultWastagePercentage, String hsnCode,
                              String description, MasterStatus status,
                              String primaryImageKey) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getSku(), p.getName(),
                p.getDesign() == null ? null : p.getDesign().getId(),
                p.getProductType().getId(),
                p.getCategory() == null ? null : p.getCategory().getId(),
                p.getBrand() == null ? null : p.getBrand().getId(),
                p.getCollection() == null ? null : p.getCollection().getId(),
                p.getDefaultMetalId(), p.getDefaultPurityId(), p.getNominalGrossWeight(),
                p.getDefaultMakingChargeType(), p.getDefaultMakingChargeValue(),
                p.getDefaultWastagePercentage(), p.getHsnCode(), p.getDescription(), p.getStatus(),
                p.getImages().stream().filter(ProductImage::isPrimaryImage)
                        .map(ProductImage::getStorageKey).findFirst().orElse(null));
    }
}
