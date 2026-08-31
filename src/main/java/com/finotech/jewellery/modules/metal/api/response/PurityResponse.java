package com.finotech.jewellery.modules.metal.api.response;

import com.finotech.jewellery.modules.metal.domain.entity.Purity;
import java.math.BigDecimal;
import java.util.UUID;

public record PurityResponse(UUID id, UUID metalId, String code, String name, BigDecimal fineness,
                             Integer displayOrder, boolean active) {

    public static PurityResponse from(Purity p) {
        return new PurityResponse(p.getId(), p.getMetal().getId(), p.getCode(), p.getName(),
                p.getFineness(), p.getDisplayOrder(), p.isActive());
    }
}
