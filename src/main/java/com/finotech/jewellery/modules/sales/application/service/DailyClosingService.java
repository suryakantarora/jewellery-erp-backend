package com.finotech.jewellery.modules.sales.application.service;

import com.finotech.jewellery.modules.payment.application.service.PaymentService;
import com.finotech.jewellery.modules.sales.api.response.DailyClosingResponse;
import com.finotech.jewellery.modules.sales.infrastructure.repository.SaleRepository;
import com.finotech.jewellery.shared.security.SecurityUtils;
import com.finotech.jewellery.shared.utils.MoneyUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The end-of-day figure a branch cashes up against: what was sold, and what was
 * collected broken down by payment method.
 */
@Service
@RequiredArgsConstructor
public class DailyClosingService {

    private final SaleRepository saleRepository;
    private final PaymentService paymentService;

    @Transactional(readOnly = true)
    public DailyClosingResponse closingFor(UUID branchId, LocalDate date) {
        SecurityUtils.requireBranchAccess(branchId);
        LocalDate day = date == null ? LocalDate.now() : date;

        BigDecimal totalSales = saleRepository.totalSalesFor(branchId, day);
        long saleCount = saleRepository.countByBranchIdAndSaleDate(branchId, day);

        List<DailyClosingResponse.MethodTotal> byMethod = paymentService
                .totalsByMethod(branchId, day).stream()
                .map(row -> new DailyClosingResponse.MethodTotal(
                        String.valueOf(row[0]), MoneyUtils.money((BigDecimal) row[1])))
                .toList();

        BigDecimal collected = byMethod.stream()
                .map(DailyClosingResponse.MethodTotal::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DailyClosingResponse(branchId, day, saleCount, MoneyUtils.money(totalSales),
                MoneyUtils.money(collected), byMethod);
    }
}
