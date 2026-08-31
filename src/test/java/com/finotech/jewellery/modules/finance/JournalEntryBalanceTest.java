package com.finotech.jewellery.modules.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finotech.jewellery.modules.finance.domain.entity.Account;
import com.finotech.jewellery.modules.finance.domain.entity.JournalEntry;
import com.finotech.jewellery.modules.finance.domain.entity.JournalEntryLine;
import com.finotech.jewellery.modules.finance.domain.enums.AccountType;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The single rule that makes a ledger a ledger: debits equal credits.
 */
class JournalEntryBalanceTest {

    @Test
    @DisplayName("a balanced entry totals correctly")
    void balancedEntryIsAccepted() {
        JournalEntry entry = new JournalEntry();
        entry.addLine(line("1010", AccountType.ASSET, "1000.00", "0"));
        entry.addLine(line("4010", AccountType.INCOME, "0", "1000.00"));

        entry.validateAndTotal();

        assertThat(entry.getTotalDebit()).isEqualByComparingTo("1000.00");
        assertThat(entry.getTotalCredit()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("an unbalanced entry is refused")
    void unbalancedEntryIsRefused() {
        JournalEntry entry = new JournalEntry();
        entry.addLine(line("1010", AccountType.ASSET, "1000.00", "0"));
        entry.addLine(line("4010", AccountType.INCOME, "0", "900.00"));

        assertThatThrownBy(entry::validateAndTotal)
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("does not balance");
    }

    @Test
    @DisplayName("an entry with no lines is refused")
    void emptyEntryIsRefused() {
        assertThatThrownBy(new JournalEntry()::validateAndTotal)
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("at least one line");
    }

    @Test
    @DisplayName("an entry for zero is refused even though it technically balances")
    void zeroEntryIsRefused() {
        JournalEntry entry = new JournalEntry();
        entry.addLine(line("1010", AccountType.ASSET, "0", "0"));
        entry.addLine(line("4010", AccountType.INCOME, "0", "0"));

        assertThatThrownBy(entry::validateAndTotal)
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be for zero");
    }

    @Test
    @DisplayName("an entry balances across many lines, not just two")
    void multiLineEntryBalances() {
        JournalEntry entry = new JournalEntry();
        entry.addLine(line("2100", AccountType.LIABILITY, "600.00", "0"));
        entry.addLine(line("1100", AccountType.ASSET, "400.00", "0"));
        entry.addLine(line("4010", AccountType.INCOME, "0", "900.00"));
        entry.addLine(line("2020", AccountType.LIABILITY, "0", "100.00"));

        entry.validateAndTotal();

        assertThat(entry.getTotalDebit()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("each account type knows which side increases it")
    void normalBalances() {
        assertThat(AccountType.ASSET.isDebitNormal()).isTrue();
        assertThat(AccountType.EXPENSE.isDebitNormal()).isTrue();
        assertThat(AccountType.LIABILITY.isDebitNormal()).isFalse();
        assertThat(AccountType.INCOME.isDebitNormal()).isFalse();
        assertThat(AccountType.EQUITY.isDebitNormal()).isFalse();

        assertThat(AccountType.INCOME.isProfitAndLoss()).isTrue();
        assertThat(AccountType.ASSET.isBalanceSheet()).isTrue();
    }

    private JournalEntryLine line(String code, AccountType type, String debit, String credit) {
        Account account = new Account();
        account.setCode(code);
        account.setAccountType(type);

        JournalEntryLine line = new JournalEntryLine();
        line.setAccount(account);
        line.setDebit(new BigDecimal(debit));
        line.setCredit(new BigDecimal(credit));
        return line;
    }
}
