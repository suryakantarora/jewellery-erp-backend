package com.finotech.jewellery.modules.gemstone.api.response;

import com.finotech.jewellery.modules.gemstone.domain.entity.Gemstone;
import java.util.UUID;

public record GemstoneResponse(UUID id, String code, String name, boolean diamond,
                               boolean precious, String description, boolean active) {

    public static GemstoneResponse from(Gemstone g) {
        return new GemstoneResponse(g.getId(), g.getCode(), g.getName(), g.isDiamond(),
                g.isPrecious(), g.getDescription(), g.isActive());
    }
}
