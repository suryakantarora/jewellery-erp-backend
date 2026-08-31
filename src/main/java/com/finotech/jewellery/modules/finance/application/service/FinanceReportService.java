package com.finotech.jewellery.modules.finance.application.service;

import com.finotech.jewellery.modules.customer.application.CustomerDirectory;
import com.finotech.jewellery.modules.finance.api.response.FinanceReports.AgeingReport;
import com.finotech.jewellery.modules.finance.api.response.FinanceReports.BalanceSheetReport;
import com.finotech.jewellery.modules.finance.api.response.FinanceReports.ProfitAndLossReport;
import com.finotech.jewellery.modules.finance.api.response.FinanceReports.TrialBalanceReport;
import com.finotech.jewellery.modules.finance.domain.enums.AccountType;
import com.finotech.jewellery.modules.finance.domain.enums.StandardAccount;
import com.finotech.jewellery.modules.finance.infrastructure.repository.LedgerQueryRepository;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.utils.MoneyUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Financial statements drawn from the ledger.
 *
 * <p>Every figure comes from posted journal entries, so a statement and the
 * ledger can never disagree. Where a statement should balance, whether it
 * actually does is reported rather than assumed — a balance sheet that quietly
 * hides a discrepancy is worse than one that shows it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceReportService {

    private static final LocalDate LEDGER_START = LocalDate.of(2000, 1, 1);

    private final LedgerQueryRepository ledger;
    private final CustomerDirectory customerDirectory;

    /** Movements and balances per account, with the balance check. */
    @Transactional(readOnly = true)
    public TrialBalanceReport trialBalance(LocalDate from, LocalDate to, UUID branchId) {
        validateRange(from, to);

        List<TrialBalanceReport.TrialBalanceLine> lines = new ArrayList<>();
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (LedgerQueryRepository.TrialBalanceRow row : ledger.trialBalance(from, to, branchId)) {
            BigDecimal closing = row.opening().add(row.periodDebit()).subtract(row.periodCredit());
            lines.add(new TrialBalanceReport.TrialBalanceLine(row.code(), row.name(),
                    AccountType.valueOf(row.accountType()), MoneyUtils.money(row.opening()),
                    MoneyUtils.money(row.periodDebit()), MoneyUtils.money(row.periodCredit()),
                    MoneyUtils.money(closing)));
            totalDebit = totalDebit.add(row.periodDebit());
            totalCredit = totalCredit.add(row.periodCredit());
        }

        boolean balanced = totalDebit.compareTo(totalCredit) == 0;
        if (!balanced) {
            log.error("Trial balance does not balance for {} to {}: debits {} credits {}",
                    from, to, totalDebit, totalCredit);
        }
        return new TrialBalanceReport(from, to, MoneyUtils.money(totalDebit),
                MoneyUtils.money(totalCredit), balanced, lines);
    }

    @Transactional(readOnly = true)
    public ProfitAndLossReport profitAndLoss(LocalDate from, LocalDate to, UUID branchId) {
        validateRange(from, to);

        List<ProfitAndLossReport.Line> income = new ArrayList<>();
        List<ProfitAndLossReport.Line> expenses = new ArrayList<>();
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        BigDecimal costOfSales = BigDecimal.ZERO;

        for (LedgerQueryRepository.TrialBalanceRow row : ledger.trialBalance(from, to, branchId)) {
            AccountType type = AccountType.valueOf(row.accountType());
            if (!type.isProfitAndLoss()) {
                continue;
            }
            // Income is credit-normal, so its movement is presented as credit
            // less debit; expenses the other way round.
            BigDecimal movement = type == AccountType.INCOME
                    ? row.periodCredit().subtract(row.periodDebit())
                    : row.periodDebit().subtract(row.periodCredit());

            if (movement.signum() == 0) {
                continue;
            }
            ProfitAndLossReport.Line line = new ProfitAndLossReport.Line(row.code(), row.name(),
                    MoneyUtils.money(movement));
            if (type == AccountType.INCOME) {
                income.add(line);
                totalIncome = totalIncome.add(movement);
            } else {
                expenses.add(line);
                totalExpense = totalExpense.add(movement);
                if (StandardAccount.COST_OF_GOODS_SOLD.code().equals(row.code())) {
                    costOfSales = costOfSales.add(movement);
                }
            }
        }

        BigDecimal grossProfit = totalIncome.subtract(costOfSales);
        BigDecimal netProfit = totalIncome.subtract(totalExpense);

        return new ProfitAndLossReport(from, to, MoneyUtils.money(totalIncome),
                MoneyUtils.money(totalExpense), MoneyUtils.money(grossProfit),
                MoneyUtils.money(netProfit), income, expenses);
    }

    /**
     * Position at a date.
     *
     * <p>Retained earnings are computed as income less expenses to date rather
     * than held in an account, so the sheet is always drawn from the same ledger
     * as everything else and cannot drift from it.
     */
    @Transactional(readOnly = true)
    public BalanceSheetReport balanceSheet(LocalDate asOf, UUID branchId) {
        List<BalanceSheetReport.Line> assets = new ArrayList<>();
        List<BalanceSheetReport.Line> liabilities = new ArrayList<>();
        List<BalanceSheetReport.Line> equity = new ArrayList<>();

        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        BigDecimal totalEquity = BigDecimal.ZERO;
        BigDecimal retainedEarnings = BigDecimal.ZERO;

        for (LedgerQueryRepository.TrialBalanceRow row
                : ledger.trialBalance(LEDGER_START, asOf, branchId)) {
            AccountType type = AccountType.valueOf(row.accountType());
            BigDecimal closing = row.opening().add(row.periodDebit()).subtract(row.periodCredit());

            if (type.isProfitAndLoss()) {
                // Income carries a credit balance, so a negative closing figure
                // is profit; expenses reduce it.
                retainedEarnings = retainedEarnings.subtract(closing);
                continue;
            }
            if (closing.signum() == 0) {
                continue;
            }

            // Liabilities and equity are credit-normal; present them positive.
            BigDecimal presented = type == AccountType.ASSET ? closing : closing.negate();
            BalanceSheetReport.Line line = new BalanceSheetReport.Line(row.code(), row.name(),
                    MoneyUtils.money(presented));

            switch (type) {
                case ASSET -> {
                    assets.add(line);
                    totalAssets = totalAssets.add(presented);
                }
                case LIABILITY -> {
                    liabilities.add(line);
                    totalLiabilities = totalLiabilities.add(presented);
                }
                case EQUITY -> {
                    equity.add(line);
                    totalEquity = totalEquity.add(presented);
                }
                default -> { }
            }
        }

        BigDecimal equityWithEarnings = totalEquity.add(retainedEarnings);
        BigDecimal difference = totalAssets.subtract(totalLiabilities.add(equityWithEarnings));

        return new BalanceSheetReport(asOf, MoneyUtils.money(totalAssets),
                MoneyUtils.money(totalLiabilities), MoneyUtils.money(equityWithEarnings),
                MoneyUtils.money(retainedEarnings), difference.signum() == 0,
                MoneyUtils.money(difference), assets, liabilities, equity);
    }

    /** Customer balances outstanding, bucketed by age. */
    @Transactional(readOnly = true)
    public AgeingReport receivablesAgeing() {
        LocalDate asOf = LocalDate.now();
        List<AgeingReport.AgeingRow> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (LedgerQueryRepository.PartyBalanceRow row
                : ledger.partyBalances(StandardAccount.ACCOUNTS_RECEIVABLE.code(), "CUSTOMER")) {
            long days = ChronoUnit.DAYS.between(row.oldestEntry(), asOf);
            String name = resolveCustomerName(row.partyId());
            rows.add(new AgeingReport.AgeingRow(row.partyId(), name,
                    MoneyUtils.money(row.balance()), row.oldestEntry(), days, bucketFor(days)));
            total = total.add(row.balance());
        }
        return new AgeingReport(asOf, "CUSTOMER", MoneyUtils.money(total), rows);
    }

    private String bucketFor(long days) {
        if (days <= 30) {
            return "0-30 days";
        }
        if (days <= 60) {
            return "31-60 days";
        }
        if (days <= 90) {
            return "61-90 days";
        }
        return "Over 90 days";
    }

    /** A deleted or unknown customer must not break the report. */
    private String resolveCustomerName(UUID customerId) {
        try {
            return customerDirectory.requireCustomer(customerId).fullName();
        } catch (RuntimeException ex) {
            return "Unknown";
        }
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ValidationException("The end of the range cannot be before the start");
        }
    }
}
