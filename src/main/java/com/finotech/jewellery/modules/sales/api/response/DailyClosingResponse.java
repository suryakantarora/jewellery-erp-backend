package com.finotech.jewellery.modules.sales.api.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The end-of-day figure a branch cashes up against.
 */
public record DailyClosingResponse(UUID branchId, LocalDate date, long saleCount,
                                   BigDecimal totalSales, BigDecimal totalCollected,
                                   List<MethodTotal> byMethod) {

    public record MethodTotal(String method, BigDecimal amount) {
    }
}
