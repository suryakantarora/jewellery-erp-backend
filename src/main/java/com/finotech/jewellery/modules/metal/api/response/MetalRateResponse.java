package com.finotech.jewellery.modules.metal.api.response;

import com.finotech.jewellery.modules.metal.domain.entity.MetalRate;
import com.finotech.jewellery.modules.metal.domain.enums.RateType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MetalRateResponse(UUID id, UUID metalId, String metalCode, UUID purityId,
                                String purityCode, RateType rateType, LocalDate effectiveDate,
                                BigDecimal ratePerUnit, String currency, UUID branchId,
                                Instant publishedAt, String publishedBy, String notes) {

    public static MetalRateResponse from(MetalRate r) {
        return new MetalRateResponse(r.getId(), r.getMetal().getId(), r.getMetal().getCode(),
                r.getPurity().getId(), r.getPurity().getCode(), r.getRateType(),
                r.getEffectiveDate(), r.getRatePerUnit(), r.getCurrency(), r.getBranchId(),
                r.getPublishedAt(), r.getPublishedBy(), r.getNotes());
    }
}
