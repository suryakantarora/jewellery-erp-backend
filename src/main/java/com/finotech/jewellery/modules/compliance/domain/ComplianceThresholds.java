package com.finotech.jewellery.modules.compliance.domain;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Thresholds that decide what is reportable.
 *
 * <p>Jurisdictions differ and limits change, so these are configuration rather
 * than constants. Laos-oriented defaults are set in {@code application.yml}.
 *
 * @param highValueSaleAmount a single sale at or above this is reportable
 * @param highValueCashAmount cash taken at or above this is reportable, which is
 *                            usually a lower bar than the sale itself
 * @param structuringWindowDays window over which repeated sales just under the
 *                              limit are treated as one pattern
 * @param structuringCount how many such sales in the window raise a flag
 */
@ConfigurationProperties(prefix = "jewellery.compliance")
public record ComplianceThresholds(BigDecimal highValueSaleAmount,
                                   BigDecimal highValueCashAmount,
                                   int structuringWindowDays,
                                   int structuringCount) {

    public ComplianceThresholds {
        if (highValueSaleAmount == null) {
            highValueSaleAmount = new BigDecimal("100000000");
        }
        if (highValueCashAmount == null) {
            highValueCashAmount = new BigDecimal("50000000");
        }
        if (structuringWindowDays <= 0) {
            structuringWindowDays = 30;
        }
        if (structuringCount <= 0) {
            structuringCount = 3;
        }
    }
}
