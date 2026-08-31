package com.finotech.jewellery.modules.metal.application;

import com.finotech.jewellery.modules.metal.domain.enums.RateType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published contract used by Pricing, Exchange and Buyback to read rates and
 * purity data. Keeps rate lookup in one place so every module values metal the
 * same way.
 */
public interface MetalRateProvider {

    /**
     * @return the rate in force for the given metal/purity on {@code onDate}
     * @throws com.finotech.jewellery.shared.exception.NotFoundException if none is published
     */
    RateView requireEffectiveRate(UUID metalId, UUID purityId, RateType rateType,
                                  LocalDate onDate, UUID branchId);

    /** Fineness of a purity, e.g. 0.9167 for 22K. */
    BigDecimal fineness(UUID purityId);

    record RateView(UUID rateId,
                    UUID metalId,
                    UUID purityId,
                    RateType rateType,
                    LocalDate effectiveDate,
                    BigDecimal ratePerUnit,
                    String currency) {
    }
}
