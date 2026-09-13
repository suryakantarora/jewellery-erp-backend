package com.finotech.jewellery.modules.product.api.response;

import com.finotech.jewellery.modules.product.domain.entity.DesignImage;
import com.finotech.jewellery.modules.product.domain.entity.JewelleryDesign;
import com.finotech.jewellery.modules.product.domain.enums.MasterStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record DesignResponse(UUID id, String designCode, String name, UUID productTypeId,
                             UUID collectionId, UUID brandId, String designer,
                             BigDecimal nominalGrossWeight, String description, MasterStatus status,
                             String primaryImageKey) {

    public static DesignResponse from(JewelleryDesign d) {
        return new DesignResponse(d.getId(), d.getDesignCode(), d.getName(),
                d.getProductType() == null ? null : d.getProductType().getId(),
                d.getCollection() == null ? null : d.getCollection().getId(),
                d.getBrand() == null ? null : d.getBrand().getId(),
                d.getDesigner(), d.getNominalGrossWeight(), d.getDescription(), d.getStatus(),
                d.getImages().stream().filter(DesignImage::isPrimaryImage)
                        .map(DesignImage::getStorageKey).findFirst().orElse(null));
    }
}
