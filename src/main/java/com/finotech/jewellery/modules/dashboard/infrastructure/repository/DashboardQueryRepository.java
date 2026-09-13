package com.finotech.jewellery.modules.dashboard.infrastructure.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only aggregate queries behind the dashboard summary.
 *
 * <p>Follows the reporting module's pattern: one grouped query per section,
 * crossing schemas by design, and never writing. Keeping these here rather
 * than as extra methods on each module's JPA repository keeps the dashboard a
 * pure reader of other modules' tables and leaves those repositories to the
 * modules that own them.
 *
 * <p>Every query takes an optional branch: {@code null} aggregates across all
 * branches, which only a super administrator without a branch reaches.
 */
@Repository
@RequiredArgsConstructor
public class DashboardQueryRepository {

    /** Same definition of a settled sale as the sales report, so the two agree. */
    private static final String SETTLED = "('CONFIRMED', 'DELIVERED', 'PARTIALLY_RETURNED')";

    private final JdbcTemplate jdbc;

    // ---------- sales ----------

    public SalesRow sales(UUID branchId, LocalDate today) {
        LocalDate monthStart = today.withDayOfMonth(1);
        String sql = """
                SELECT COUNT(*) FILTER (WHERE s.sale_date = ?) AS today_count,
                       COALESCE(SUM(s.total_amount) FILTER (WHERE s.sale_date = ?), 0) AS today_total,
                       COALESCE(SUM(s.total_amount), 0) AS month_total,
                       MAX(s.currency) AS currency
                FROM sales.sale s
                WHERE s.status IN %s
                  AND s.sale_date BETWEEN ? AND ?
                  AND (?::uuid IS NULL OR s.branch_id = ?::uuid)
                """.formatted(SETTLED);
        String branch = branchId == null ? null : branchId.toString();
        return jdbc.queryForObject(sql, (rs, i) -> new SalesRow(
                        rs.getLong("today_count"), rs.getBigDecimal("today_total"),
                        rs.getBigDecimal("month_total"), rs.getString("currency")),
                today, today, monthStart, today, branch, branch);
    }

    // ---------- inventory ----------

    /** Items per status with their cost and price sums; one query serves count and value tiles. */
    public List<ItemStatusRow> itemsByStatus(UUID branchId) {
        String sql = """
                SELECT status, COUNT(*) AS item_count,
                       COALESCE(SUM(total_cost), 0) AS cost_value,
                       COALESCE(SUM(current_price), 0) AS retail_value,
                       MAX(currency) AS currency
                FROM inventory.jewellery_item
                WHERE (?::uuid IS NULL OR current_branch_id = ?::uuid)
                GROUP BY status
                """;
        String branch = branchId == null ? null : branchId.toString();
        return jdbc.query(sql, (rs, i) -> new ItemStatusRow(rs.getString("status"),
                        rs.getLong("item_count"), rs.getBigDecimal("cost_value"),
                        rs.getBigDecimal("retail_value"), rs.getString("currency")),
                branch, branch);
    }

    /**
     * Monitored locations whose sellable stock is at or below their threshold —
     * the platform's only low-stock concept (see {@code LowStockMonitor}).
     */
    public long lowStockLocations(UUID branchId) {
        String sql = """
                SELECT COUNT(*)
                FROM organization.location l
                WHERE l.low_stock_threshold IS NOT NULL
                  AND l.status = 'ACTIVE'
                  AND (?::uuid IS NULL OR l.branch_id = ?::uuid)
                  AND (SELECT COUNT(*) FROM inventory.jewellery_item ji
                       WHERE ji.current_location_id = l.id AND ji.status = 'AVAILABLE')
                      <= l.low_stock_threshold
                """;
        String branch = branchId == null ? null : branchId.toString();
        Long count = jdbc.queryForObject(sql, Long.class, branch, branch);
        return count == null ? 0 : count;
    }

    // ---------- transfers ----------

    /**
     * Open transfers touching the branch, grouped finely enough that the
     * service can derive pending, second-approval, inbound and outbound
     * figures without a second round trip.
     */
    public List<TransferRow> openTransfers(UUID branchId) {
        String sql = """
                SELECT status, (approved_by IS NOT NULL) AS first_approved,
                       from_branch_id, to_branch_id, COUNT(*) AS movement_count
                FROM inventory.inventory_movement
                WHERE movement_type = 'TRANSFER'
                  AND status IN ('PENDING_APPROVAL', 'DISPATCHED')
                  AND (?::uuid IS NULL OR from_branch_id = ?::uuid OR to_branch_id = ?::uuid)
                GROUP BY status, (approved_by IS NOT NULL), from_branch_id, to_branch_id
                """;
        String branch = branchId == null ? null : branchId.toString();
        return jdbc.query(sql, (rs, i) -> new TransferRow(rs.getString("status"),
                        rs.getBoolean("first_approved"), uuid(rs.getString("from_branch_id")),
                        uuid(rs.getString("to_branch_id")), rs.getLong("movement_count")),
                branch, branch, branch);
    }

    // ---------- procurement and approvals ----------

    public List<StatusCountRow> purchaseOrdersByStatus(UUID branchId) {
        return statusCounts("procurement.purchase_order", branchId);
    }

    public long requisitionsPendingApproval(UUID branchId) {
        return countWithStatus("procurement.purchase_requisition", "PENDING_APPROVAL", branchId);
    }

    public long goodsReceiptsPendingQualityCheck(UUID branchId) {
        return countWithStatus("procurement.goods_receipt", "PENDING_QUALITY_CHECK", branchId);
    }

    public long exchangesPendingApproval(UUID branchId) {
        return countWithStatus("sales.exchange_intake", "PENDING_APPROVAL", branchId);
    }

    public long stockCountsPendingReview(UUID branchId) {
        return countWithStatus("inventory.stock_count", "PENDING_REVIEW", branchId);
    }

    // ---------- repairs ----------

    public List<StatusCountRow> repairsByStatus(UUID branchId) {
        return statusCounts("sales.repair_request", branchId);
    }

    // ---------- metal rates ----------

    /**
     * The selling rate in force today for every metal/purity, resolved the same
     * way {@code MetalRateRepository.findEffectiveRates} does: latest effective
     * date wins, a branch-specific rate beats the company-wide one, and the
     * most recent publication breaks any remaining tie.
     */
    public List<MetalRateRow> currentSellingRates(UUID branchId, LocalDate today) {
        String sql = """
                SELECT DISTINCT ON (r.metal_id, r.purity_id)
                       r.metal_id, m.name AS metal_name, r.purity_id, pu.code AS purity_code,
                       r.rate_type, r.rate_per_unit, r.currency, r.published_at
                FROM product.metal_rate r
                JOIN product.metal m ON m.id = r.metal_id
                JOIN product.purity pu ON pu.id = r.purity_id
                WHERE r.rate_type = 'SELLING'
                  AND r.effective_date <= ?
                  AND (r.branch_id IS NULL OR r.branch_id = ?::uuid)
                ORDER BY r.metal_id, r.purity_id, r.effective_date DESC,
                         (r.branch_id IS NOT NULL) DESC, r.published_at DESC
                """;
        String branch = branchId == null ? null : branchId.toString();
        return jdbc.query(sql, (rs, i) -> new MetalRateRow(
                        UUID.fromString(rs.getString("metal_id")), rs.getString("metal_name"),
                        UUID.fromString(rs.getString("purity_id")), rs.getString("purity_code"),
                        rs.getString("rate_type"), rs.getBigDecimal("rate_per_unit"),
                        rs.getString("currency"), rs.getTimestamp("published_at").toInstant()),
                today, branch);
    }

    // ---------- organization ----------

    public Optional<String> branchName(UUID branchId) {
        List<String> names = jdbc.query("SELECT name FROM organization.branch WHERE id = ?",
                (rs, i) -> rs.getString("name"), branchId);
        return names.stream().findFirst();
    }

    /** The primary branch recorded on the user, which the token does not carry. */
    public Optional<UUID> primaryBranchOf(UUID userId) {
        List<String> ids = jdbc.query(
                "SELECT primary_branch_id FROM identity.app_user WHERE id = ?",
                (rs, i) -> rs.getString("primary_branch_id"), userId);
        return ids.stream().filter(java.util.Objects::nonNull).findFirst().map(UUID::fromString);
    }

    // ---------- helpers and row shapes ----------

    private List<StatusCountRow> statusCounts(String table, UUID branchId) {
        String sql = """
                SELECT status, COUNT(*) AS row_count
                FROM %s
                WHERE (?::uuid IS NULL OR branch_id = ?::uuid)
                GROUP BY status
                """.formatted(table);
        String branch = branchId == null ? null : branchId.toString();
        return jdbc.query(sql, (rs, i) -> new StatusCountRow(rs.getString("status"),
                rs.getLong("row_count")), branch, branch);
    }

    private long countWithStatus(String table, String status, UUID branchId) {
        String sql = """
                SELECT COUNT(*) FROM %s
                WHERE status = ?
                  AND (?::uuid IS NULL OR branch_id = ?::uuid)
                """.formatted(table);
        String branch = branchId == null ? null : branchId.toString();
        Long count = jdbc.queryForObject(sql, Long.class, status, branch, branch);
        return count == null ? 0 : count;
    }

    private static UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    public record SalesRow(long todayCount, BigDecimal todayTotal, BigDecimal monthToDateTotal,
                           String currency) {
    }

    public record ItemStatusRow(String status, long count, BigDecimal costValue,
                                BigDecimal retailValue, String currency) {
    }

    public record TransferRow(String status, boolean firstApproved, UUID fromBranchId,
                              UUID toBranchId, long count) {
    }

    public record StatusCountRow(String status, long count) {
    }

    public record MetalRateRow(UUID metalId, String metalName, UUID purityId, String purityCode,
                               String rateType, BigDecimal rate, String currency,
                               Instant publishedAt) {
    }
}
