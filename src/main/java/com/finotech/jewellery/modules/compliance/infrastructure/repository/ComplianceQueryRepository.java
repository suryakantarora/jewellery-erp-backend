package com.finotech.jewellery.modules.compliance.infrastructure.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only reporting queries.
 *
 * <p>These deliberately use SQL and cross schemas, which no business module is
 * allowed to do. Reporting is the one place where that is correct: a compliance
 * report is a question about the business as a whole, and answering it by
 * calling each module in turn would be both slow and less accurate. Nothing here
 * writes, so the ownership rule that protects data integrity is not at risk.
 */
@Repository
@RequiredArgsConstructor
public class ComplianceQueryRepository {

    private final JdbcTemplate jdbc;

    /** Settled sales at or above the reportable amount, with KYC state attached. */
    public List<HighValueRow> highValueSales(LocalDate from, LocalDate to, BigDecimal threshold,
                                             UUID branchId) {
        String sql = """
                SELECT s.id, s.sale_number, s.invoice_number, s.sale_date, s.branch_id,
                       c.id AS customer_id, c.full_name, c.customer_code, c.kyc_status,
                       s.total_amount,
                       COALESCE((SELECT SUM(p.amount) FROM payment.payment p
                                 WHERE p.sale_id = s.id
                                   AND p.method = 'CASH'
                                   AND p.direction = 'INBOUND'
                                   AND p.status = 'CAPTURED'), 0) AS cash_amount
                FROM sales.sale s
                JOIN customer.customer c ON c.id = s.customer_id
                WHERE s.status IN ('CONFIRMED', 'DELIVERED', 'PARTIALLY_RETURNED')
                  AND s.sale_date BETWEEN ? AND ?
                  AND s.total_amount >= ?
                  AND (?::uuid IS NULL OR s.branch_id = ?::uuid)
                ORDER BY s.total_amount DESC
                """;
        String branch = branchId == null ? null : branchId.toString();
        return jdbc.query(sql, (rs, i) -> new HighValueRow(
                        UUID.fromString(rs.getString("id")), rs.getString("sale_number"),
                        rs.getString("invoice_number"), rs.getDate("sale_date").toLocalDate(),
                        rs.getString("branch_id") == null
                                ? null : UUID.fromString(rs.getString("branch_id")),
                        UUID.fromString(rs.getString("customer_id")), rs.getString("full_name"),
                        rs.getString("customer_code"), rs.getString("kyc_status"),
                        rs.getBigDecimal("total_amount"), rs.getBigDecimal("cash_amount")),
                from, to, threshold, branch, branch);
    }

    /**
     * Customers with several sales just below the threshold in a short window —
     * the shape of a purchase deliberately split to stay under a reporting limit.
     */
    public List<StructuringRow> possibleStructuring(LocalDate from, BigDecimal threshold,
                                                    int minimumCount) {
        String sql = """
                SELECT c.id, c.customer_code, c.full_name, COUNT(*) AS sale_count,
                       SUM(s.total_amount) AS total_value
                FROM sales.sale s
                JOIN customer.customer c ON c.id = s.customer_id
                WHERE s.status IN ('CONFIRMED', 'DELIVERED', 'PARTIALLY_RETURNED')
                  AND s.sale_date >= ?
                  AND s.total_amount < ?
                  AND s.total_amount >= ? * 0.5
                GROUP BY c.id, c.customer_code, c.full_name
                HAVING COUNT(*) >= ?
                ORDER BY SUM(s.total_amount) DESC
                """;
        return jdbc.query(sql, (rs, i) -> new StructuringRow(
                        UUID.fromString(rs.getString("id")), rs.getString("customer_code"),
                        rs.getString("full_name"), rs.getLong("sale_count"),
                        rs.getBigDecimal("total_value")),
                from, threshold, threshold, minimumCount);
    }

    public List<KycCountRow> kycCounts() {
        return jdbc.query("""
                SELECT kyc_status, COUNT(*) AS total
                FROM customer.customer
                WHERE status <> 'INACTIVE'
                GROUP BY kyc_status
                """, (rs, i) -> new KycCountRow(rs.getString("kyc_status"), rs.getLong("total")));
    }

    /**
     * Customers whose KYC needs attention: pending review, or verified against a
     * document that has since expired.
     */
    public List<KycExceptionRow> kycExceptions(LocalDate asOf, int limit) {
        String sql = """
                SELECT c.id, c.customer_code, c.full_name, c.kyc_status,
                       MIN(d.expiry_date) AS earliest_expiry,
                       COALESCE((SELECT SUM(s.total_amount) FROM sales.sale s
                                 WHERE s.customer_id = c.id
                                   AND s.status IN ('CONFIRMED','DELIVERED','PARTIALLY_RETURNED')), 0)
                           AS lifetime_spend,
                       CASE
                           WHEN c.kyc_status = 'PENDING' THEN 'Awaiting verification'
                           WHEN c.kyc_status = 'REJECTED' THEN 'Verification was rejected'
                           WHEN c.kyc_status = 'VERIFIED'
                                AND MIN(d.expiry_date) < ? THEN 'Verified against an expired document'
                           ELSE 'Review required'
                       END AS issue
                FROM customer.customer c
                LEFT JOIN customer.customer_document d ON d.customer_id = c.id
                WHERE c.status = 'ACTIVE'
                  AND (c.kyc_status IN ('PENDING', 'REJECTED', 'EXPIRED')
                       OR (c.kyc_status = 'VERIFIED'
                           AND EXISTS (SELECT 1 FROM customer.customer_document x
                                       WHERE x.customer_id = c.id AND x.expiry_date < ?)))
                GROUP BY c.id, c.customer_code, c.full_name, c.kyc_status
                ORDER BY lifetime_spend DESC
                LIMIT ?
                """;
        return jdbc.query(sql, (rs, i) -> new KycExceptionRow(
                        UUID.fromString(rs.getString("id")), rs.getString("customer_code"),
                        rs.getString("full_name"), rs.getString("kyc_status"),
                        rs.getString("issue"),
                        rs.getDate("earliest_expiry") == null
                                ? null : rs.getDate("earliest_expiry").toLocalDate(),
                        rs.getBigDecimal("lifetime_spend")),
                asOf, asOf, limit);
    }

    /** Vault movements, stock counts and buyback valuations that carry approvals. */
    public List<ControlRow> dualAuthorisationActions(LocalDate from, LocalDate to, UUID branchId) {
        String sql = """
                SELECT 'VAULT_MOVEMENT' AS control_type, m.id, m.reference_number AS reference,
                       m.to_branch_id AS branch_id, m.approved_by, m.approved_at,
                       m.second_approved_by, m.second_approved_at, m.status::text AS status,
                       NULL AS note
                FROM inventory.inventory_movement m
                WHERE m.requires_approval = true
                  AND m.created_at::date BETWEEN ? AND ?
                  AND (?::uuid IS NULL OR m.to_branch_id = ?::uuid)

                UNION ALL

                SELECT 'STOCK_COUNT', sc.id, sc.reference_number, sc.branch_id,
                       sc.approved_by, sc.approved_at, sc.second_approved_by, sc.second_approved_at,
                       sc.status::text,
                       CASE WHEN sc.missing_count > 0 OR sc.unexpected_count > 0
                            THEN 'Variance: ' || sc.missing_count || ' missing, '
                                 || sc.unexpected_count || ' unexpected'
                            ELSE NULL END
                FROM inventory.stock_count sc
                WHERE sc.count_date BETWEEN ? AND ?
                  AND (?::uuid IS NULL OR sc.branch_id = ?::uuid)

                UNION ALL

                SELECT 'BUYBACK_VALUATION', e.id, e.reference_number, e.branch_id,
                       e.valued_by, e.valued_at, e.approved_by, e.approved_at,
                       e.status::text,
                       'Payout ' || COALESCE(e.net_valuation::text, '0')
                FROM sales.exchange_intake e
                WHERE e.received_date BETWEEN ? AND ?
                  AND e.valued_by IS NOT NULL
                  AND (?::uuid IS NULL OR e.branch_id = ?::uuid)

                ORDER BY 6 DESC NULLS LAST
                """;
        String branch = branchId == null ? null : branchId.toString();
        return jdbc.query(sql, (rs, i) -> new ControlRow(
                        rs.getString("control_type"), UUID.fromString(rs.getString("id")),
                        rs.getString("reference"),
                        rs.getString("branch_id") == null
                                ? null : UUID.fromString(rs.getString("branch_id")),
                        rs.getString("approved_by"),
                        rs.getTimestamp("approved_at") == null
                                ? null : rs.getTimestamp("approved_at").toInstant(),
                        rs.getString("second_approved_by"),
                        rs.getTimestamp("second_approved_at") == null
                                ? null : rs.getTimestamp("second_approved_at").toInstant(),
                        rs.getString("status"), rs.getString("note")),
                from, to, branch, branch, from, to, branch, branch, from, to, branch, branch);
    }

    public List<ActionCountRow> auditCountsByAction(Instant from, Instant to) {
        return jdbc.query("""
                SELECT action, entity_type, COUNT(*) AS total
                FROM audit.audit_log
                WHERE occurred_at BETWEEN ? AND ?
                GROUP BY action, entity_type
                ORDER BY COUNT(*) DESC
                """, (rs, i) -> new ActionCountRow(rs.getString("action"),
                rs.getString("entity_type"), rs.getLong("total")),
                java.sql.Timestamp.from(from), java.sql.Timestamp.from(to));
    }

    public List<ActorCountRow> auditCountsByUser(Instant from, Instant to,
                                                 List<String> sensitiveActions) {
        String placeholders = String.join(",", java.util.Collections.nCopies(
                sensitiveActions.size(), "?"));
        String sql = """
                SELECT COALESCE(username, 'system') AS username, COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE action IN (%s)) AS sensitive_total
                FROM audit.audit_log
                WHERE occurred_at BETWEEN ? AND ?
                GROUP BY COALESCE(username, 'system')
                ORDER BY COUNT(*) DESC
                """.formatted(placeholders);

        Object[] args = new Object[sensitiveActions.size() + 2];
        for (int i = 0; i < sensitiveActions.size(); i++) {
            args[i] = sensitiveActions.get(i);
        }
        args[sensitiveActions.size()] = java.sql.Timestamp.from(from);
        args[sensitiveActions.size() + 1] = java.sql.Timestamp.from(to);

        return jdbc.query(sql, (rs, i) -> new ActorCountRow(rs.getString("username"),
                rs.getLong("total"), rs.getLong("sensitive_total")), args);
    }

    public List<SensitiveEventRow> sensitiveAuditEvents(Instant from, Instant to,
                                                        List<String> sensitiveActions, int limit) {
        String placeholders = String.join(",", java.util.Collections.nCopies(
                sensitiveActions.size(), "?"));
        String sql = """
                SELECT id, username, action, entity_type, entity_id, branch_id, occurred_at
                FROM audit.audit_log
                WHERE occurred_at BETWEEN ? AND ?
                  AND action IN (%s)
                ORDER BY occurred_at DESC
                LIMIT ?
                """.formatted(placeholders);

        Object[] args = new Object[sensitiveActions.size() + 3];
        args[0] = java.sql.Timestamp.from(from);
        args[1] = java.sql.Timestamp.from(to);
        for (int i = 0; i < sensitiveActions.size(); i++) {
            args[i + 2] = sensitiveActions.get(i);
        }
        args[sensitiveActions.size() + 2] = limit;

        return jdbc.query(sql, (rs, i) -> new SensitiveEventRow(
                UUID.fromString(rs.getString("id")), rs.getString("username"),
                rs.getString("action"), rs.getString("entity_type"), rs.getString("entity_id"),
                rs.getString("branch_id") == null
                        ? null : UUID.fromString(rs.getString("branch_id")),
                rs.getTimestamp("occurred_at").toInstant()), args);
    }

    // ---------- row shapes ----------

    public record HighValueRow(UUID saleId, String saleNumber, String invoiceNumber,
                               LocalDate saleDate, UUID branchId, UUID customerId,
                               String customerName, String customerCode, String kycStatus,
                               BigDecimal totalAmount, BigDecimal cashAmount) {
    }

    public record StructuringRow(UUID customerId, String customerCode, String customerName,
                                 long saleCount, BigDecimal totalValue) {
    }

    public record KycCountRow(String kycStatus, long total) {
    }

    public record KycExceptionRow(UUID customerId, String customerCode, String customerName,
                                  String kycStatus, String issue, LocalDate documentExpiry,
                                  BigDecimal lifetimeSpend) {
    }

    public record ControlRow(String controlType, UUID entityId, String reference, UUID branchId,
                             String firstApprover, Instant firstApprovedAt, String secondApprover,
                             Instant secondApprovedAt, String status, String note) {
    }

    public record ActionCountRow(String action, String entityType, long count) {
    }

    public record ActorCountRow(String username, long count, long sensitiveCount) {
    }

    public record SensitiveEventRow(UUID id, String username, String action, String entityType,
                                    String entityId, UUID branchId, Instant occurredAt) {
    }
}
