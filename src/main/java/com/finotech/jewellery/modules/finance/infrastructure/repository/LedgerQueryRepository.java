package com.finotech.jewellery.modules.finance.infrastructure.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Ledger aggregates: trial balance, account movements and party balances.
 *
 * <p>Only posted entries are ever included. A draft entry is not yet part of the
 * books, and letting one leak into a balance would make every downstream report
 * wrong in a way nobody would notice.
 */
@Repository
@RequiredArgsConstructor
public class LedgerQueryRepository {

    /**
     * Entries that count toward a balance.
     *
     * <p>A reversed entry stays in the ledger alongside the reversal that
     * cancels it: the two net to zero, and the history of what was posted and
     * then corrected remains visible. Excluding only the original would leave
     * the reversal standing on its own and silently misstate every balance it
     * touches.
     */
    private static final String POSTED = "e.status IN ('POSTED', 'REVERSED')";

    private final JdbcTemplate jdbc;

    /** Net movement per account over a period, with the opening balance. */
    public List<TrialBalanceRow> trialBalance(LocalDate from, LocalDate to, UUID branchId) {
        String sql = """
                SELECT a.id, a.code, a.name, a.account_type,
                       COALESCE(SUM(CASE WHEN e.entry_date < ? THEN l.debit - l.credit END), 0)
                           AS opening,
                       COALESCE(SUM(CASE WHEN e.entry_date BETWEEN ? AND ? THEN l.debit END), 0)
                           AS period_debit,
                       COALESCE(SUM(CASE WHEN e.entry_date BETWEEN ? AND ? THEN l.credit END), 0)
                           AS period_credit
                FROM finance.account a
                JOIN finance.journal_entry_line l ON l.account_id = a.id
                JOIN finance.journal_entry e ON e.id = l.journal_entry_id
                WHERE %s
                  AND e.entry_date <= ?
                  AND (?::uuid IS NULL OR e.branch_id = ?::uuid)
                GROUP BY a.id, a.code, a.name, a.account_type
                HAVING COALESCE(SUM(l.debit - l.credit), 0) <> 0
                    OR COALESCE(SUM(CASE WHEN e.entry_date BETWEEN ? AND ? THEN l.debit END), 0) <> 0
                ORDER BY a.code
                """.formatted(POSTED);
        String branch = branchId == null ? null : branchId.toString();
        return jdbc.query(sql, (rs, i) -> new TrialBalanceRow(
                        UUID.fromString(rs.getString("id")), rs.getString("code"),
                        rs.getString("name"), rs.getString("account_type"),
                        rs.getBigDecimal("opening"), rs.getBigDecimal("period_debit"),
                        rs.getBigDecimal("period_credit")),
                from, from, to, from, to, to, branch, branch, from, to);
    }

    /** Every posted line on one account, for drilling into a balance. */
    public List<LedgerLineRow> accountLedger(UUID accountId, LocalDate from, LocalDate to,
                                             int limit) {
        String sql = """
                SELECT e.entry_number, e.entry_date, e.description, e.source::text AS source,
                       e.reference_type, e.reference_id, l.debit, l.credit, l.description AS line_note
                FROM finance.journal_entry_line l
                JOIN finance.journal_entry e ON e.id = l.journal_entry_id
                WHERE l.account_id = ?
                  AND %s
                  AND e.entry_date BETWEEN ? AND ?
                ORDER BY e.entry_date, e.entry_number
                LIMIT ?
                """.formatted(POSTED);
        return jdbc.query(sql, (rs, i) -> new LedgerLineRow(
                        rs.getString("entry_number"), rs.getDate("entry_date").toLocalDate(),
                        rs.getString("description"), rs.getString("source"),
                        rs.getString("reference_type"), rs.getString("reference_id"),
                        rs.getBigDecimal("debit"), rs.getBigDecimal("credit"),
                        rs.getString("line_note")),
                accountId, from, to, limit);
    }

    /**
     * Outstanding balance per party on one account — the basis of receivables
     * and payables ageing.
     */
    public List<PartyBalanceRow> partyBalances(String accountCode, String partyType) {
        String sql = """
                SELECT l.party_id,
                       COALESCE(SUM(l.debit - l.credit), 0) AS balance,
                       MIN(e.entry_date) AS oldest_entry
                FROM finance.journal_entry_line l
                JOIN finance.journal_entry e ON e.id = l.journal_entry_id
                JOIN finance.account a ON a.id = l.account_id
                WHERE a.code = ?
                  AND l.party_type = ?
                  AND l.party_id IS NOT NULL
                  AND %s
                GROUP BY l.party_id
                HAVING COALESCE(SUM(l.debit - l.credit), 0) <> 0
                ORDER BY ABS(COALESCE(SUM(l.debit - l.credit), 0)) DESC
                """.formatted(POSTED);
        return jdbc.query(sql, (rs, i) -> new PartyBalanceRow(
                        UUID.fromString(rs.getString("party_id")), rs.getBigDecimal("balance"),
                        rs.getDate("oldest_entry").toLocalDate()),
                accountCode, partyType);
    }

    /** Balance of one account as at a date, for the balance sheet. */
    public BigDecimal balanceAsAt(String accountCode, LocalDate asOf, UUID branchId) {
        String sql = """
                SELECT COALESCE(SUM(l.debit - l.credit), 0)
                FROM finance.journal_entry_line l
                JOIN finance.journal_entry e ON e.id = l.journal_entry_id
                JOIN finance.account a ON a.id = l.account_id
                WHERE a.code = ? AND %s AND e.entry_date <= ?
                  AND (?::uuid IS NULL OR e.branch_id = ?::uuid)
                """.formatted(POSTED);
        String branch = branchId == null ? null : branchId.toString();
        return jdbc.queryForObject(sql, BigDecimal.class, accountCode, asOf, branch, branch);
    }

    public record TrialBalanceRow(UUID accountId, String code, String name, String accountType,
                                  BigDecimal opening, BigDecimal periodDebit,
                                  BigDecimal periodCredit) {
    }

    public record LedgerLineRow(String entryNumber, LocalDate entryDate, String description,
                                String source, String referenceType, String referenceId,
                                BigDecimal debit, BigDecimal credit, String lineNote) {
    }

    public record PartyBalanceRow(UUID partyId, BigDecimal balance, LocalDate oldestEntry) {
    }
}
