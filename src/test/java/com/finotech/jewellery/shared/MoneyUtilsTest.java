package com.finotech.jewellery.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.finotech.jewellery.shared.utils.MoneyUtils;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyUtilsTest {

    @Test
    @DisplayName("money keeps two decimals with half-up rounding")
    void moneyScale() {
        assertThat(MoneyUtils.money(new BigDecimal("1850000.555"))).isEqualByComparingTo("1850000.56");
        assertThat(MoneyUtils.money(new BigDecimal("1850000.554"))).isEqualByComparingTo("1850000.55");
    }

    @Test
    @DisplayName("weight keeps three decimals, as jewellery scales report")
    void weightScale() {
        assertThat(MoneyUtils.weight(new BigDecimal("12.5"))).isEqualByComparingTo("12.500");
        assertThat(MoneyUtils.weight(new BigDecimal("12.4567"))).isEqualByComparingTo("12.457");
    }

    @Test
    @DisplayName("a metal value keeps full precision through the calculation")
    void metalValueIsExact() {
        // 12.350 g at 1,850,000 per gram must not drift the way a double would.
        BigDecimal value = new BigDecimal("12.350").multiply(new BigDecimal("1850000"));
        assertThat(MoneyUtils.money(value)).isEqualByComparingTo("22847500.00");
    }
}
