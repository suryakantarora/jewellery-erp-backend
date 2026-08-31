package com.finotech.jewellery.modules.gemstone.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Published contract the inventory module uses to attach stones to a jewellery
 * item and to read their aggregate weight and value.
 */
public interface StoneRegistry {

    /** Replaces the stone rows of an item and returns their totals. */
    StoneTotals replaceStonesOf(UUID jewelleryItemId, List<StoneSpec> stones);

    StoneTotals totalsFor(UUID jewelleryItemId);

    void removeStonesOf(UUID jewelleryItemId);

    record StoneSpec(UUID gemstoneId,
                     UUID certificateId,
                     int stoneCount,
                     BigDecimal caratWeight,
                     String shape,
                     String cut,
                     String colour,
                     String clarity,
                     String settingType,
                     BigDecimal ratePerCarat,
                     BigDecimal stoneValue,
                     BigDecimal weightGrams,
                     String notes) {
    }

    record StoneTotals(int stoneCount, BigDecimal totalCarat, BigDecimal totalWeightGrams,
                       BigDecimal totalValue) {
    }
}
