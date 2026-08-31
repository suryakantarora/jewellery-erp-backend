package com.finotech.jewellery.modules.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finotech.jewellery.CommerceFixture;
import com.finotech.jewellery.IntegrationTestBase;
import com.finotech.jewellery.TestSecurity;
import com.finotech.jewellery.modules.finance.api.request.FinanceRequests;
import com.finotech.jewellery.modules.finance.api.response.FinanceReports.TrialBalanceReport;
import com.finotech.jewellery.modules.finance.application.service.AccountService;
import com.finotech.jewellery.modules.finance.application.service.FinanceReportService;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * The ledger must stay internally consistent through every path that writes to
 * it, including corrections.
 */
@Import(LedgerIntegrationTest.Fixtures.class)
class LedgerIntegrationTest extends IntegrationTestBase {

    @TestConfiguration
    static class Fixtures {
        @Bean
        CommerceFixture commerceFixture(
                com.finotech.jewellery.modules.organization.application.service.OrganizationService o,
                com.finotech.jewellery.modules.metal.application.service.MetalService m,
                com.finotech.jewellery.modules.metal.application.service.MetalRateService r,
                com.finotech.jewellery.modules.product.application.service.ProductService p,
                com.finotech.jewellery.modules.product.application.service.ProductMasterService pm,
                com.finotech.jewellery.modules.pricing.application.service.PricingConfigService pc,
                com.finotech.jewellery.modules.customer.application.service.CustomerService c,
                com.finotech.jewellery.modules.inventory.application.service.JewelleryItemService i) {
            return new CommerceFixture(o, m, r, p, pm, pc, c, i);
        }
    }

    @Autowired private AccountService accountService;
    @Autowired private FinanceReportService reportService;

    @BeforeEach
    void authenticate() {
        TestSecurity.authenticateAsSuperAdmin();
    }

    @AfterEach
    void clearAuthentication() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("a manual journal posts and leaves the trial balance balanced")
    void manualJournalKeepsTheLedgerBalanced() {
        accountService.postManualEntry(new FinanceRequests.ManualJournalRequest(
                LocalDate.now(), "Capital introduced", null,
                List.of(new FinanceRequests.ManualJournalRequest.Line("1020",
                                new BigDecimal("50000000"), null, "Into the bank"),
                        new FinanceRequests.ManualJournalRequest.Line("3010",
                                null, new BigDecimal("50000000"), "Share capital"))));

        TrialBalanceReport trialBalance = reportService.trialBalance(
                LocalDate.now().minusDays(1), LocalDate.now(), null);

        assertThat(trialBalance.balanced()).isTrue();
        assertThat(trialBalance.totalDebit()).isEqualByComparingTo(trialBalance.totalCredit());
    }

    @Test
    @DisplayName("an unbalanced manual journal is refused")
    void unbalancedManualJournalIsRefused() {
        assertThatThrownBy(() -> accountService.postManualEntry(
                new FinanceRequests.ManualJournalRequest(LocalDate.now(), "Wrong", null,
                        List.of(new FinanceRequests.ManualJournalRequest.Line("1020",
                                        new BigDecimal("100"), null, null),
                                new FinanceRequests.ManualJournalRequest.Line("3010",
                                        null, new BigDecimal("90"), null)))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("does not balance");
    }

    @Test
    @DisplayName("a line cannot be both a debit and a credit")
    void lineCannotBeBothSides() {
        assertThatThrownBy(() -> accountService.postManualEntry(
                new FinanceRequests.ManualJournalRequest(LocalDate.now(), "Wrong", null,
                        List.of(new FinanceRequests.ManualJournalRequest.Line("1020",
                                new BigDecimal("100"), new BigDecimal("100"), null)))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("both a debit and a credit");
    }

    @Test
    @DisplayName("posting to an unknown account is refused")
    void unknownAccountIsRefused() {
        assertThatThrownBy(() -> accountService.postManualEntry(
                new FinanceRequests.ManualJournalRequest(LocalDate.now(), "Wrong", null,
                        List.of(new FinanceRequests.ManualJournalRequest.Line("9999",
                                        new BigDecimal("100"), null, null),
                                new FinanceRequests.ManualJournalRequest.Line("3010",
                                        null, new BigDecimal("100"), null)))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("a reversal cancels the original, leaving the account at zero")
    void reversalNetsToZero() {
        // Covers a real defect: the reversed entry was dropped from the ledger
        // while its reversal stayed, so the pair misstated the balance instead
        // of cancelling out.
        var original = accountService.postManualEntry(new FinanceRequests.ManualJournalRequest(
                LocalDate.now(), "To be reversed", null,
                List.of(new FinanceRequests.ManualJournalRequest.Line("1020",
                                new BigDecimal("7000000"), null, null),
                        new FinanceRequests.ManualJournalRequest.Line("3010",
                                null, new BigDecimal("7000000"), null))));

        BigDecimal afterPosting = bankBalance();
        accountService.reverseEntry(original.id(), "Entered in error");
        BigDecimal afterReversal = bankBalance();

        assertThat(afterPosting.subtract(afterReversal)).isEqualByComparingTo("7000000.00");
        assertThat(reportService.trialBalance(LocalDate.now().minusDays(1), LocalDate.now(), null)
                .balanced()).isTrue();
    }

    @Test
    @DisplayName("an entry cannot be reversed twice")
    void doubleReversalIsRefused() {
        var original = accountService.postManualEntry(new FinanceRequests.ManualJournalRequest(
                LocalDate.now(), "Reverse once", null,
                List.of(new FinanceRequests.ManualJournalRequest.Line("1020",
                                new BigDecimal("500"), null, null),
                        new FinanceRequests.ManualJournalRequest.Line("3010",
                                null, new BigDecimal("500"), null))));
        accountService.reverseEntry(original.id(), "First");

        assertThatThrownBy(() -> accountService.reverseEntry(original.id(), "Second"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("an account the automatic postings depend on cannot be deactivated")
    void systemAccountsAreProtected() {
        UUID cashAccountId = accountService.listAccounts().stream()
                .filter(a -> "1010".equals(a.code()))
                .findFirst().orElseThrow().id();

        assertThatThrownBy(() -> accountService.deactivateAccount(cashAccountId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be deactivated");
    }

    @Test
    @DisplayName("the chart of accounts is seeded with the accounts the rules need")
    void standardAccountsExist() {
        List<String> codes = accountService.listAccounts().stream()
                .map(a -> a.code()).toList();

        assertThat(codes).contains("1010", "1020", "1100", "1200", "2020", "2100", "2110",
                "4010", "5010");
    }

    private BigDecimal bankBalance() {
        return reportService.balanceSheet(LocalDate.now(), null).assets().stream()
                .filter(l -> "1020".equals(l.accountCode()))
                .map(l -> l.amount())
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }
}
