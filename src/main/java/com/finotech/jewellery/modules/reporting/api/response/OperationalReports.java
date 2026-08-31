package com.finotech.jewellery.modules.reporting.api.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Operational reports (section 22). All read-only, and all derived from what the
 * business modules already record.
 */
public final class OperationalReports {

    private OperationalReports() {
    }

    /** Sales over a period, broken down the ways a manager actually asks for. */
    public record SalesReport(LocalDate from, LocalDate to, long saleCount,
                              BigDecimal grossSales, BigDecimal totalDiscount,
                              BigDecimal totalTax, BigDecimal netSales,
                              BigDecimal averageOrderValue, List<PeriodRow> byDay,
                              List<DimensionRow> byBranch, List<DimensionRow> byProductType,
                              List<StaffRow> bySalesperson) {

        public record PeriodRow(LocalDate date, long saleCount, BigDecimal totalAmount) {
        }

        public record DimensionRow(UUID id, String name, long saleCount, BigDecimal totalAmount) {
        }

        public record StaffRow(UUID salespersonId, String username, long saleCount,
                               BigDecimal totalAmount) {
        }
    }

    /**
     * What is on hand and what it is worth.
     *
     * <p>Two valuations are given deliberately: cost is what the business paid,
     * and metal value is what the stock is worth at today's rate. For a
     * jeweller the gap between them is the position, not a rounding difference.
     */
    public record InventoryValuationReport(LocalDate asOf, long itemCount,
                                           BigDecimal totalGrossWeight,
                                           BigDecimal totalNetMetalWeight,
                                           BigDecimal totalCost,
                                           BigDecimal totalMetalValueAtToday,
                                           List<LocationRow> byLocation,
                                           List<MetalRow> byMetal,
                                           List<StatusRow> byStatus) {

        public record LocationRow(UUID locationId, String locationCode, String locationName,
                                  long itemCount, BigDecimal netWeight, BigDecimal totalCost) {
        }

        public record MetalRow(UUID metalId, String metalCode, String purityCode, long itemCount,
                               BigDecimal netWeight, BigDecimal totalCost,
                               BigDecimal ratePerUnit, BigDecimal metalValue) {
        }

        public record StatusRow(String status, long itemCount, BigDecimal totalCost) {
        }
    }

    /**
     * Stock that has not moved. Ageing capital is the quietest cost in a
     * jewellery business, so the buckets are deliberately coarse and visible.
     */
    public record StockAgeingReport(LocalDate asOf, long totalItems, BigDecimal totalCost,
                                    List<AgeBucket> buckets, List<AgedItem> oldestItems) {

        public record AgeBucket(String label, int fromDays, Integer toDays, long itemCount,
                                BigDecimal totalCost) {
        }

        public record AgedItem(UUID itemId, String itemCode, UUID productId, int daysInStock,
                               BigDecimal totalCost, UUID locationId) {
        }
    }

    /**
     * Outstanding loyalty points and what they would cost to honour — a real
     * liability, not a marketing number.
     */
    public record LoyaltyLiabilityReport(LocalDate asOf, long enrolledCustomers,
                                         long outstandingPoints, BigDecimal liabilityValue,
                                         long pointsExpiringWithin90Days,
                                         BigDecimal valueExpiringWithin90Days,
                                         List<TierRow> byTier) {

        public record TierRow(String tierCode, String tierName, long customerCount,
                              long pointsBalance, BigDecimal liabilityValue) {
        }
    }
}
