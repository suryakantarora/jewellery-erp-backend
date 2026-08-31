package com.finotech.jewellery.modules.reporting.application.service;

import com.finotech.jewellery.modules.reporting.api.response.OperationalReports.InventoryValuationReport;
import com.finotech.jewellery.modules.reporting.api.response.OperationalReports.LoyaltyLiabilityReport;
import com.finotech.jewellery.modules.reporting.api.response.OperationalReports.SalesReport;
import com.finotech.jewellery.modules.reporting.api.response.OperationalReports.StockAgeingReport;
import com.finotech.jewellery.modules.reporting.infrastructure.repository.ReportingQueryRepository;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.utils.MoneyUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operational reporting (section 22).
 */
@Service
@RequiredArgsConstructor
public class ReportingService {

    /** A year of daily rows is already a lot to return in one response. */
    private static final int MAX_REPORT_DAYS = 366;
    private static final int AGED_ITEM_LIMIT = 50;

    private final ReportingQueryRepository queries;

    @Transactional(readOnly = true)
    public SalesReport salesReport(LocalDate from, LocalDate to, UUID branchId) {
        validateRange(from, to);

        ReportingQueryRepository.SalesTotalsRow totals = queries.salesTotals(from, to, branchId);
        BigDecimal average = totals.saleCount() == 0
                ? BigDecimal.ZERO
                : totals.netSales().divide(BigDecimal.valueOf(totals.saleCount()),
                        MoneyUtils.MONEY_SCALE, RoundingMode.HALF_UP);

        List<SalesReport.PeriodRow> byDay = queries.salesByDay(from, to, branchId).stream()
                .map(r -> new SalesReport.PeriodRow(r.date(), r.saleCount(),
                        MoneyUtils.money(r.totalAmount())))
                .toList();

        // Branch breakdown only makes sense when not already filtered to one.
        List<SalesReport.DimensionRow> byBranch = branchId != null ? List.of()
                : queries.salesByBranch(from, to).stream()
                        .map(r -> new SalesReport.DimensionRow(r.id(), r.name(), r.saleCount(),
                                MoneyUtils.money(r.totalAmount())))
                        .toList();

        List<SalesReport.DimensionRow> byProductType =
                queries.salesByProductType(from, to, branchId).stream()
                        .map(r -> new SalesReport.DimensionRow(r.id(), r.name(), r.saleCount(),
                                MoneyUtils.money(r.totalAmount())))
                        .toList();

        List<SalesReport.StaffRow> bySalesperson =
                queries.salesBySalesperson(from, to, branchId).stream()
                        .map(r -> new SalesReport.StaffRow(r.id(), r.username(), r.saleCount(),
                                MoneyUtils.money(r.totalAmount())))
                        .toList();

        return new SalesReport(from, to, totals.saleCount(),
                MoneyUtils.money(totals.grossSales()), MoneyUtils.money(totals.totalDiscount()),
                MoneyUtils.money(totals.totalTax()), MoneyUtils.money(totals.netSales()),
                MoneyUtils.money(average), byDay, byBranch, byProductType, bySalesperson);
    }

    /**
     * Stock on hand at cost and at today's metal value.
     *
     * <p>A purity with no published rate contributes zero metal value rather
     * than being guessed at — an understated figure a user can question beats a
     * plausible one they cannot.
     */
    @Transactional(readOnly = true)
    public InventoryValuationReport inventoryValuation(UUID branchId) {
        LocalDate asOf = LocalDate.now();
        ReportingQueryRepository.InventoryTotalsRow totals = queries.inventoryTotals(branchId);

        List<InventoryValuationReport.LocationRow> byLocation =
                queries.inventoryByLocation(branchId).stream()
                        .map(r -> new InventoryValuationReport.LocationRow(r.locationId(), r.code(),
                                r.name(), r.itemCount(), MoneyUtils.weight(r.netWeight()),
                                MoneyUtils.money(r.totalCost())))
                        .toList();

        List<InventoryValuationReport.MetalRow> byMetal = new ArrayList<>();
        BigDecimal metalValueTotal = BigDecimal.ZERO;
        for (var r : queries.inventoryByMetal(branchId, asOf)) {
            BigDecimal value = r.ratePerUnit() == null
                    ? BigDecimal.ZERO
                    : r.netWeight().multiply(r.ratePerUnit());
            metalValueTotal = metalValueTotal.add(value);
            byMetal.add(new InventoryValuationReport.MetalRow(r.metalId(), r.metalCode(),
                    r.purityCode(), r.itemCount(), MoneyUtils.weight(r.netWeight()),
                    MoneyUtils.money(r.totalCost()), r.ratePerUnit(), MoneyUtils.money(value)));
        }

        List<InventoryValuationReport.StatusRow> byStatus =
                queries.inventoryByStatus(branchId).stream()
                        .map(r -> new InventoryValuationReport.StatusRow(r.status(), r.itemCount(),
                                MoneyUtils.money(r.totalCost())))
                        .toList();

        return new InventoryValuationReport(asOf, totals.itemCount(),
                MoneyUtils.weight(totals.grossWeight()), MoneyUtils.weight(totals.netWeight()),
                MoneyUtils.money(totals.totalCost()), MoneyUtils.money(metalValueTotal),
                byLocation, byMetal, byStatus);
    }

    /** Sellable stock bucketed by how long it has sat unsold. */
    @Transactional(readOnly = true)
    public StockAgeingReport stockAgeing(UUID branchId) {
        record Bucket(String label, int from, Integer to) {
        }
        List<Bucket> definitions = List.of(
                new Bucket("0-30 days", 0, 30),
                new Bucket("31-90 days", 31, 90),
                new Bucket("91-180 days", 91, 180),
                new Bucket("181-365 days", 181, 365),
                new Bucket("Over a year", 366, null));

        List<StockAgeingReport.AgeBucket> buckets = new ArrayList<>();
        long totalItems = 0;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (Bucket definition : definitions) {
            var row = queries.ageBucket(branchId, definition.from(), definition.to());
            buckets.add(new StockAgeingReport.AgeBucket(definition.label(), definition.from(),
                    definition.to(), row.itemCount(), MoneyUtils.money(row.totalCost())));
            totalItems += row.itemCount();
            totalCost = totalCost.add(row.totalCost());
        }

        List<StockAgeingReport.AgedItem> oldest =
                queries.agedStock(branchId, AGED_ITEM_LIMIT).stream()
                        .map(r -> new StockAgeingReport.AgedItem(r.itemId(), r.itemCode(),
                                r.productId(), r.daysInStock(), MoneyUtils.money(r.totalCost()),
                                r.locationId()))
                        .toList();

        return new StockAgeingReport(LocalDate.now(), totalItems, MoneyUtils.money(totalCost),
                buckets, oldest);
    }

    /**
     * Outstanding loyalty points valued at what they would cost to honour.
     *
     * <p>Points expiring soon are shown separately: that portion of the
     * liability will fall away on its own, which changes how it should be read.
     */
    @Transactional(readOnly = true)
    public LoyaltyLiabilityReport loyaltyLiability() {
        var totals = queries.loyaltyLiability();
        var expiring = queries.loyaltyExpiringWithin(90);

        List<LoyaltyLiabilityReport.TierRow> byTier = queries.loyaltyByTier().stream()
                .map(r -> new LoyaltyLiabilityReport.TierRow(r.tierCode(), r.tierName(),
                        r.customerCount(), r.pointsBalance(),
                        MoneyUtils.money(r.liabilityValue())))
                .toList();

        return new LoyaltyLiabilityReport(LocalDate.now(), totals.enrolled(), totals.points(),
                MoneyUtils.money(totals.value()), expiring.points(),
                MoneyUtils.money(expiring.value()), byTier);
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ValidationException("The end of the range cannot be before the start");
        }
        long days = ChronoUnit.DAYS.between(from, to);
        if (days > MAX_REPORT_DAYS) {
            throw new ValidationException("A report may cover at most " + MAX_REPORT_DAYS
                    + " days; the requested range is " + days);
        }
    }
}
