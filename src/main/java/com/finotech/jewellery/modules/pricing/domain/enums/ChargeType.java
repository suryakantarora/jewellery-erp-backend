package com.finotech.jewellery.modules.pricing.domain.enums;

/**
 * How a making charge is expressed. Jewellery retail uses all three, often for
 * different product families in the same shop.
 */
public enum ChargeType {
    /** value × net metal weight */
    PER_GRAM,
    /** value% of the metal value */
    PERCENTAGE_OF_METAL,
    /** a fixed amount per item */
    FLAT
}
