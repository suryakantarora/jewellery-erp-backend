package com.finotech.jewellery.modules.metal.api.response;

import com.finotech.jewellery.modules.metal.domain.entity.Metal;
import java.util.UUID;

public record MetalResponse(UUID id, String code, String name, String symbol, String weightUnit,
                            String description, boolean active) {

    public static MetalResponse from(Metal m) {
        return new MetalResponse(m.getId(), m.getCode(), m.getName(), m.getSymbol(),
                m.getWeightUnit(), m.getDescription(), m.isActive());
    }
}
