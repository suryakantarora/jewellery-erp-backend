package com.finotech.jewellery.modules.finance.api.response;

import com.finotech.jewellery.modules.finance.domain.entity.Account;
import com.finotech.jewellery.modules.finance.domain.entity.JournalEntry;
import com.finotech.jewellery.modules.finance.domain.enums.AccountType;
import com.finotech.jewellery.modules.finance.domain.enums.JournalSource;
import com.finotech.jewellery.modules.finance.domain.enums.JournalStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class FinanceReports {

    private FinanceReports() {
    }

    public record AccountResponse(UUID id, String code, String name, AccountType accountType,
                                  UUID parentId, boolean postable, boolean systemAccount,
                                  String currency, boolean active) {

        public static AccountResponse from(Account a) {
            return new AccountResponse(a.getId(), a.getCode(), a.getName(), a.getAccountType(),
                    a.getParent() == null ? null : a.getParent().getId(), a.isPostable(),
                    a.isSystemAccount(), a.getCurrency(), a.isActive());
        }
    }

    public record JournalEntryResponse(UUID id, String entryNumber, LocalDate entryDate,
                                       JournalSource source, JournalStatus status,
                                       String description, String referenceType,
                                       String referenceId, UUID branchId, BigDecimal totalDebit,
                                       BigDecimal totalCredit, Instant postedAt, String postedBy,
                                       UUID reversalOfId, UUID reversedById,
                                       List<JournalLineResponse> lines) {

        public record JournalLineResponse(UUID accountId, String accountCode, String accountName,
                                          BigDecimal debit, BigDecimal credit, String description,
                                          String partyType, UUID partyId) {
        }

        public static JournalEntryResponse from(JournalEntry e) {
            List<JournalLineResponse> lines = e.getLines().stream()
                    .map(l -> new JournalLineResponse(l.getAccount().getId(),
                            l.getAccount().getCode(), l.getAccount().getName(), l.getDebit(),
                            l.getCredit(), l.getDescription(), l.getPartyType(), l.getPartyId()))
                    .toList();
            return new JournalEntryResponse(e.getId(), e.getEntryNumber(), e.getEntryDate(),
                    e.getSource(), e.getStatus(), e.getDescription(), e.getReferenceType(),
                    e.getReferenceId(), e.getBranchId(), e.getTotalDebit(), e.getTotalCredit(),
                    e.getPostedAt(), e.getPostedBy(), e.getReversalOfId(), e.getReversedById(),
                    lines);
        }

        /** Header only, for list endpoints. */
        public static JournalEntryResponse summary(JournalEntry e) {
            return new JournalEntryResponse(e.getId(), e.getEntryNumber(), e.getEntryDate(),
                    e.getSource(), e.getStatus(), e.getDescription(), e.getReferenceType(),
                    e.getReferenceId(), e.getBranchId(), e.getTotalDebit(), e.getTotalCredit(),
                    e.getPostedAt(), e.getPostedBy(), e.getReversalOfId(), e.getReversedById(),
                    null);
        }
    }

    /**
     * The proof that the ledger is internally consistent: total debits must
     * equal total credits.
     */
    public record TrialBalanceReport(LocalDate from, LocalDate to, BigDecimal totalDebit,
                                     BigDecimal totalCredit, boolean balanced,
                                     List<TrialBalanceLine> lines) {

        public record TrialBalanceLine(String accountCode, String accountName,
                                       AccountType accountType, BigDecimal openingBalance,
                                       BigDecimal periodDebit, BigDecimal periodCredit,
                                       BigDecimal closingBalance) {
        }
    }

    /** Income less expenses over a period. */
    public record ProfitAndLossReport(LocalDate from, LocalDate to, BigDecimal totalIncome,
                                      BigDecimal totalExpense, BigDecimal grossProfit,
                                      BigDecimal netProfit, List<Line> income,
                                      List<Line> expenses) {

        public record Line(String accountCode, String accountName, BigDecimal amount) {
        }
    }

    /**
     * Position at a date. Retained earnings are derived from income less
     * expenses to date rather than stored, so the sheet always balances against
     * the same ledger it is drawn from.
     */
    public record BalanceSheetReport(LocalDate asOf, BigDecimal totalAssets,
                                     BigDecimal totalLiabilities, BigDecimal totalEquity,
                                     BigDecimal retainedEarnings, boolean balanced,
                                     BigDecimal difference, List<Line> assets,
                                     List<Line> liabilities, List<Line> equity) {

        public record Line(String accountCode, String accountName, BigDecimal amount) {
        }
    }

    /** What customers owe, bucketed by how long it has been outstanding. */
    public record AgeingReport(LocalDate asOf, String partyType, BigDecimal totalOutstanding,
                               List<AgeingRow> rows) {

        public record AgeingRow(UUID partyId, String partyName, BigDecimal outstanding,
                                LocalDate oldestEntry, long daysOutstanding, String bucket) {
        }
    }
}
