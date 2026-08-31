package com.finotech.jewellery.modules.reporting.infrastructure.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only aggregate queries for operational reporting.
 *
 * <p>Like the compliance queries, these cross schemas by design: a report is a
 * question about the business as a whole. They only read, so module ownership of
 * writes is untouched.
 *
 * <p>Section 22 notes that reporting starts against PostgreSQL and later moves
 * behind ETL to a warehouse. Keeping every report query in one class per area is
 * what makes that move a replacement rather than a rewrite.
 */
@Repository
@RequiredArgsConstructor
public class ReportingQueryRepository {

    private static final String SETTLED = "('CONFIRMED', 'DELIVERED', 'PARTIALLY_RETURNED')";

    private final JdbcTemplate jdbc;

    // ---------- sales ----------

    public SalesTotalsRow salesTotals(LocalDate from, LocalDate to, UUID branchId) {
        String sql = """
                SELECT COUNT(*) AS sale_count,
                       COALESCE(SUM(s.sub_total), 0) AS gross_sales,
                       COALESCE(SUM(s.discount_total), 0) AS total_discount,
                       COALESCE(SUM(s.tax_total), 0) AS total_tax,
                       COALESCE(SUM(s.total_amount), 0) AS net_sales
                FROM sales.sale s
                WHERE s.status IN %s
                  AND s.sale_date BETWEEN ? AND ?
                  AND (?::uuid IS NULL OR s.branch_id = ?::uuid)
                """.formatted(SETTLED);
        String branch = branchId == null ? null : branchId.toString();
        return jdbc.queryForObject(sql, (rs, i) -> new SalesTotalsRow(
                        rs.getLong("sale_count"), rs.getBigDecimal("gross_sales"),
                        rs.getBigDecimal("total_discount"), rs.getBigDecimal("total_tax"),
                        rs.getBigDecimal("net_sales")),
                from, to, branch, branch);
    }

    public List<DayRow> salesByDay(LocalDate from, LocalDate to, UUID branchId) {
        String sql = """
                SELECT s.sale_date, COUNT(*) AS sale_count,
                       COALESCE(SUM(s.total_amount), 0) AS total_amount
                FROM sales.sale s
                WHERE s.status IN %s
                  AND s.sale_date BETWEEN ? AND ?
                  AND (?::uuid IS NULL OR s.branch_id = ?::uuid)
                GROUP BY s.sale_date
                ORDER BY s.sale_date
                """.formatted(SETTLED);
        String branch = branchId == null ? null : branchId.toString();
        return jdbc.query(sql, (rs, i) -> new DayRow(rs.getDate("sale_date").toLocalDate(),
                rs.getLong("sale_count"), rs.getBigDecimal("total_amount")),
                from, to, branch, branch);
    }

    public List<NamedRow> salesByBranch(LocalDate from, LocalDate to) {
        String sql = """
                SELECT b.id, b.name, COUNT(*) AS sale_count,
                       COALESCE(SUM(s.total_amount), 0) AS total_amount
                FROM sales.sale s
                JOIN organization.branch b ON b.id = s.branch_id
                WHERE s.status IN %s AND s.sale_date BETWEEN ? AND ?
                GROUP BY b.id, b.name
                ORDER BY SUM(s.total_amount) DESC
                """.formatted(SETTLED);
        return jdbc.query(sql, namedMapper(), from, to);
    }

    public List<NamedRow> salesByProductType(LocalDate from, LocalDate to, UUID branchId) {
        String sql = """
                SELECT pt.id, pt.name, COUNT(*) AS sale_count,
                       COALESCE(SUM(sl.line_total), 0) AS total_amount
                FROM sales.sale_line sl
                JOIN sales.sale s ON s.id = sl.sale_id
                JOIN inventory.jewellery_item ji ON ji.id = sl.jewellery_item_id
                JOIN product.product p ON p.id = ji.product_id
                JOIN product.product_type pt ON pt.id = p.product_type_id
                WHERE s.status IN %s
                  AND s.sale_date BETWEEN ? AND ?
                  AND (?::uuid IS NULL OR s.branch_id = ?::uuid)
                GROUP BY pt.id, pt.name
                ORDER BY SUM(sl.line_total) DESC
                """.formatted(SETTLED);
        String branch = branchId == null ? null : branchId.toString();
        return jdbc.query(sql, namedMapper(), from, to, branch, branch);
    }

    public List<StaffRow> salesBySalesperson(LocalDate from, LocalDate to, UUID branchId) {
        String sql = """
                SELECT u.id, u.username, COUNT(*) AS sale_count,
                       COALESCE(SUM(s.total_amount), 0) AS total_amount
                FROM sales.sale s
                JOIN identity.app_user u ON u.id = s.salesperson_id
                WHERE s.status IN %s
                  AND s.sale_date BETWEEN ? AND ?
                  AND (?::uuid IS NULL OR s.branch_id = ?::uuid)
                GROUP BY u.id, u.username
                ORDER BY SUM(s.total_amount) DESC
                """.formatted(SETTLED);
        String branch = branchId == null ? null : branchId.toString();
        return jdbc.query(sql, (rs, i) -> new StaffRow(
                        UUID.fromString(rs.getString("id")), rs.getString("username"),
                        rs.getLong("sale_count"), rs.getBigDecimal("total_amount")),
                from, to, branch, branch);
    }

    // ---------- inventory ----------

    /** Only stock physically on hand: sold and scrapped items are not inventory. */
    private static final String IN_STOCK = "('AVAILABLE', 'RESERVED', 'IN_TRANSIT', 'UNDER_REPAIR')";

    public InventoryTotalsRow inventoryTotals(UUID branchId) {
        String sql = """
                SELECT COUNT(*) AS item_count,
                       COALESCE(SUM(gross_weight), 0) AS gross_weight,
                       COALESCE(SUM(net_metal_weight), 0) AS net_weight,
                       COALESCE(SUM(total_cost), 0) AS total_cost
                FROM inventory.jewellery_item
                WHERE status IN %s
                  AND (?::uuid IS NULL OR current_branch_id = ?::uuid)
                """.formatted(IN_STOCK);
        String branch = branchId == null ? null : branchId.toString();
        return jdbc.queryForObject(sql, (rs, i) -> new InventoryTotalsRow(
                        rs.getLong("item_count"), rs.getBigDecimal("gross_weight"),
                        rs.getBigDecimal("net_weight"), rs.getBigDecimal("total_cost")),
                branch, branch);
    }

    public List<InventoryLocationRow> inventoryByLocation(UUID branchId) {
        String sql = """
                SELECT l.id, l.code, l.name, COUNT(*) AS item_count,
                       COALESCE(SUM(ji.net_metal_weight), 0) AS net_weight,
                       COALESCE(SUM(ji.total_cost), 0) AS total_cost
                FROM inventory.jewellery_item ji
                JOIN organization.location l ON l.id = ji.current_location_id
                WHERE ji.status IN %s
                  AND (?::uuid IS NULL OR ji.current_branch_id = ?::uuid)
                GROUP BY l.id, l.code, l.name
                ORDER BY SUM(ji.total_cost) DESC
                """.formatted(IN_STOCK);
        String branch = branchId == null ? null : branchId.toString();
        return jdbc.query(sql, (rs, i) -> new InventoryLocationRow(
                        UUID.fromString(rs.getString("id")), rs.getString("code"),
                        rs.getString("name"), rs.getLong("item_count"),
                        rs.getBigDecimal("net_weight"), rs.getBigDecimal("total_cost")),
                branch, branch);
    }

    /**
     * Stock by metal and purity, valued at the rate in force today. The rate is
     * resolved in SQL so one query covers every purity held.
     */
    public List<InventoryMetalRow> inventoryByMetal(UUID branchId, LocalDate asOf) {
        String sql = """
                SELECT m.id AS metal_id, m.code AS metal_code, pu.code AS purity_code,
                       COUNT(*) AS item_count,
                       COALESCE(SUM(ji.net_metal_weight), 0) AS net_weight,
                       COALESCE(SUM(ji.total_cost), 0) AS total_cost,
                       (SELECT r.rate_per_unit FROM product.metal_rate r
                        WHERE r.metal_id = m.id AND r.purity_id = pu.id
                          AND r.rate_type = 'SELLING' AND r.effective_date <= ?
                        ORDER BY r.effective_date DESC, r.published_at DESC
                        LIMIT 1) AS rate_per_unit
                FROM inventory.jewellery_item ji
                JOIN product.metal m ON m.id = ji.metal_id
                JOIN product.purity pu ON pu.id = ji.purity_id
                WHERE ji.status IN %s
                  AND (?::uuid IS NULL OR ji.current_branch_id = ?::uuid)
                GROUP BY m.id, m.code, pu.id, pu.code
                ORDER BY m.code, pu.code
                """.formatted(IN_STOCK);
        String branch = branchId == null ? null : branchId.toString();
        return jdbc.query(sql, (rs, i) -> new InventoryMetalRow(
                        UUID.fromString(rs.getString("metal_id")), rs.getString("metal_code"),
                        rs.getString("purity_code"), rs.getLong("item_count"),
                        rs.getBigDecimal("net_weight"), rs.getBigDecimal("total_cost"),
                        rs.getBigDecimal("rate_per_unit")),
                asOf, branch, branch);
    }

    public List<StatusRow> inventoryByStatus(UUID branchId) {
        String sql = """
                SELECT status, COUNT(*) AS item_count, COALESCE(SUM(total_cost), 0) AS total_cost
                FROM inventory.jewellery_item
                WHERE (?::uuid IS NULL OR current_branch_id = ?::uuid)
                GROUP BY status
                ORDER BY COUNT(*) DESC
                """;
        String branch = branchId == null ? null : branchId.toString();
        return jdbc.query(sql, (rs, i) -> new StatusRow(rs.getString("status"),
                rs.getLong("item_count"), rs.getBigDecimal("total_cost")), branch, branch);
    }

    /** Days in stock measured from receipt, or from creation when never received. */
    public List<AgedItemRow> agedStock(UUID branchId, int limit) {
        String sql = """
                SELECT id, item_code, product_id, current_location_id,
                       COALESCE(total_cost, 0) AS total_cost,
                       (CURRENT_DATE - COALESCE(received_date, created_at::date)) AS days_in_stock
                FROM inventory.jewellery_item
                WHERE status IN ('AVAILABLE', 'RESERVED')
                  AND (?::uuid IS NULL OR current_branch_id = ?::uuid)
                ORDER BY days_in_stock DESC
                LIMIT ?
                """;
        String branch = branchId == null ? null : branchId.toString();
        return jdbc.query(sql, (rs, i) -> new AgedItemRow(
                        UUID.fromString(rs.getString("id")), rs.getString("item_code"),
                        UUID.fromString(rs.getString("product_id")),
                        rs.getString("current_location_id") == null
                                ? null : UUID.fromString(rs.getString("current_location_id")),
                        rs.getInt("days_in_stock"), rs.getBigDecimal("total_cost")),
                branch, branch, limit);
    }

    public AgeBucketRow ageBucket(UUID branchId, int fromDays, Integer toDays) {
        String sql = """
                SELECT COUNT(*) AS item_count, COALESCE(SUM(total_cost), 0) AS total_cost
                FROM inventory.jewellery_item
                WHERE status IN ('AVAILABLE', 'RESERVED')
                  AND (?::uuid IS NULL OR current_branch_id = ?::uuid)
                  AND (CURRENT_DATE - COALESCE(received_date, created_at::date)) >= ?
                  AND (?::int IS NULL
                       OR (CURRENT_DATE - COALESCE(received_date, created_at::date)) <= ?::int)
                """;
        String branch = branchId == null ? null : branchId.toString();
        return jdbc.queryForObject(sql, (rs, i) -> new AgeBucketRow(
                        rs.getLong("item_count"), rs.getBigDecimal("total_cost")),
                branch, branch, fromDays, toDays, toDays);
    }

    // ---------- loyalty liability ----------

    public LoyaltyLiabilityRow loyaltyLiability() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS enrolled,
                       COALESCE(SUM(a.points_balance), 0) AS outstanding_points,
                       COALESCE(SUM(a.points_balance * p.currency_value_per_point), 0) AS liability
                FROM crm.loyalty_account a
                JOIN crm.loyalty_program p ON p.id = a.program_id
                WHERE a.active = true
                """, (rs, i) -> new LoyaltyLiabilityRow(rs.getLong("enrolled"),
                rs.getLong("outstanding_points"), rs.getBigDecimal("liability")));
    }

    public LoyaltyLiabilityRow loyaltyExpiringWithin(int days) {
        return jdbc.queryForObject("""
                SELECT 0 AS enrolled,
                       COALESCE(SUM(t.points), 0) AS outstanding_points,
                       COALESCE(SUM(t.points * p.currency_value_per_point), 0) AS liability
                FROM crm.loyalty_transaction t
                JOIN crm.loyalty_account a ON a.id = t.account_id
                JOIN crm.loyalty_program p ON p.id = a.program_id
                WHERE t.transaction_type = 'EARN'
                  AND t.expired = false
                  AND t.expires_on IS NOT NULL
                  AND t.expires_on <= CURRENT_DATE + (? || ' days')::interval
                """, (rs, i) -> new LoyaltyLiabilityRow(0, rs.getLong("outstanding_points"),
                rs.getBigDecimal("liability")), days);
    }

    public List<LoyaltyTierRow> loyaltyByTier() {
        return jdbc.query("""
                SELECT COALESCE(t.code, 'UNRANKED') AS tier_code,
                       COALESCE(t.name, 'No tier') AS tier_name,
                       COUNT(*) AS customer_count,
                       COALESCE(SUM(a.points_balance), 0) AS points_balance,
                       COALESCE(SUM(a.points_balance * p.currency_value_per_point), 0) AS liability
                FROM crm.loyalty_account a
                JOIN crm.loyalty_program p ON p.id = a.program_id
                LEFT JOIN crm.loyalty_tier t ON t.id = a.tier_id
                WHERE a.active = true
                GROUP BY t.code, t.name, t.minimum_points
                ORDER BY COALESCE(t.minimum_points, -1)
                """, (rs, i) -> new LoyaltyTierRow(rs.getString("tier_code"),
                rs.getString("tier_name"), rs.getLong("customer_count"),
                rs.getLong("points_balance"), rs.getBigDecimal("liability")));
    }

    // ---------- helpers and row shapes ----------

    private org.springframework.jdbc.core.RowMapper<NamedRow> namedMapper() {
        return (rs, i) -> new NamedRow(UUID.fromString(rs.getString("id")), rs.getString("name"),
                rs.getLong("sale_count"), rs.getBigDecimal("total_amount"));
    }

    public record SalesTotalsRow(long saleCount, BigDecimal grossSales, BigDecimal totalDiscount,
                                 BigDecimal totalTax, BigDecimal netSales) {
    }

    public record DayRow(LocalDate date, long saleCount, BigDecimal totalAmount) {
    }

    public record NamedRow(UUID id, String name, long saleCount, BigDecimal totalAmount) {
    }

    public record StaffRow(UUID id, String username, long saleCount, BigDecimal totalAmount) {
    }

    public record InventoryTotalsRow(long itemCount, BigDecimal grossWeight, BigDecimal netWeight,
                                     BigDecimal totalCost) {
    }

    public record InventoryLocationRow(UUID locationId, String code, String name, long itemCount,
                                       BigDecimal netWeight, BigDecimal totalCost) {
    }

    public record InventoryMetalRow(UUID metalId, String metalCode, String purityCode,
                                    long itemCount, BigDecimal netWeight, BigDecimal totalCost,
                                    BigDecimal ratePerUnit) {
    }

    public record StatusRow(String status, long itemCount, BigDecimal totalCost) {
    }

    public record AgedItemRow(UUID itemId, String itemCode, UUID productId, UUID locationId,
                              int daysInStock, BigDecimal totalCost) {
    }

    public record AgeBucketRow(long itemCount, BigDecimal totalCost) {
    }

    public record LoyaltyLiabilityRow(long enrolled, long points, BigDecimal value) {
    }

    public record LoyaltyTierRow(String tierCode, String tierName, long customerCount,
                                 long pointsBalance, BigDecimal liabilityValue) {
    }
}
