package com.finotech.jewellery.shared.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * All money and weight arithmetic goes through BigDecimal with fixed scales;
 * floating point is never used for either (section 12).
 */
public final class MoneyUtils {

    public static final int MONEY_SCALE = 2;
    public static final int WEIGHT_SCALE = 3;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private MoneyUtils() {
    }

    public static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(MONEY_SCALE, ROUNDING);
    }

    public static BigDecimal weight(BigDecimal value) {
        return value == null ? null : value.setScale(WEIGHT_SCALE, ROUNDING);
    }

    public static BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
