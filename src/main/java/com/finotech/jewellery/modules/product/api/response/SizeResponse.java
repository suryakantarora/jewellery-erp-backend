package com.finotech.jewellery.modules.product.api.response;

import com.finotech.jewellery.modules.product.domain.entity.Size;
import com.finotech.jewellery.modules.product.domain.enums.MasterStatus;
import java.util.UUID;

public record SizeResponse(UUID id, UUID productTypeId, String code, String label,
                           String standard, Integer displayOrder, MasterStatus status) {

    public static SizeResponse from(Size s) {
        return new SizeResponse(s.getId(), s.getProductType().getId(), s.getCode(), s.getLabel(),
                s.getStandard(), s.getDisplayOrder(), s.getStatus());
    }
}
