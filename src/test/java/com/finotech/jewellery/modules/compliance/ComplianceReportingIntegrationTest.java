package com.finotech.jewellery.modules.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finotech.jewellery.CommerceFixture;
import com.finotech.jewellery.IntegrationTestBase;
import com.finotech.jewellery.TestSecurity;
import com.finotech.jewellery.modules.compliance.application.service.ComplianceReportService;
import com.finotech.jewellery.modules.customer.api.request.CustomerDocumentRequest;
import com.finotech.jewellery.modules.customer.application.service.CustomerService;
import com.finotech.jewellery.modules.payment.api.request.RecordPaymentRequest;
import com.finotech.jewellery.modules.payment.application.service.PaymentService;
import com.finotech.jewellery.modules.payment.domain.enums.PaymentMethod;
import com.finotech.jewellery.modules.reporting.application.service.ReportingService;
import com.finotech.jewellery.modules.sales.api.request.CreateSaleRequest;
import com.finotech.jewellery.modules.sales.api.response.SaleResponse;
import com.finotech.jewellery.modules.sales.application.service.SaleService;
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
 * Compliance reports decide what gets escalated to a regulator, so the
 * thresholds must include and exclude exactly the right transactions.
 */
@Import(ComplianceReportingIntegrationTest.Fixtures.class)
class ComplianceReportingIntegrationTest extends IntegrationTestBase {

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

    @Autowired private CommerceFixture fixture;
    @Autowired private SaleService saleService;
    @Autowired private PaymentService paymentService;
    @Autowired private CustomerService customerService;
    @Autowired private ComplianceReportService complianceService;
    @Autowired private ReportingService reportingService;

    @BeforeEach
    void authenticate() {
        TestSecurity.authenticateAsSuperAdmin();
    }

    @AfterEach
    void clearAuthentication() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("a sale above the threshold is reported and one below it is not")
    void highValueThresholdIncludesAndExcludes() {
        CommerceFixture.World world = fixture.create(new BigDecimal("1850000"),
                new BigDecimal("45000"), BigDecimal.ZERO, null);

        // 70 g comes to roughly 132.6M; 5 g to roughly 9.5M.
        settledSale(world, new BigDecimal("70.000"));
        settledSale(world, new BigDecimal("5.000"));

        var report = complianceService.highValueTransactions(
                LocalDate.now().minusDays(1), LocalDate.now(), world.branchId(),
                new BigDecimal("100000000"));

        assertThat(report.transactionCount()).isEqualTo(1);
        assertThat(report.transactions().get(0).totalAmount())
                .isGreaterThan(new BigDecimal("100000000"));
    }

    @Test
    @DisplayName("lowering the threshold pulls in the smaller sale")
    void thresholdIsHonoured() {
        CommerceFixture.World world = fixture.create(new BigDecimal("1850000"),
                new BigDecimal("45000"), BigDecimal.ZERO, null);
        settledSale(world, new BigDecimal("70.000"));
        settledSale(world, new BigDecimal("5.000"));

        var report = complianceService.highValueTransactions(
                LocalDate.now().minusDays(1), LocalDate.now(), world.branchId(),
                new BigDecimal("1000000"));

        assertThat(report.transactionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("a high-value sale to an unverified customer is counted as such")
    void unverifiedCustomersAreCounted() {
        CommerceFixture.World world = fixture.create(new BigDecimal("1850000"),
                new BigDecimal("45000"), BigDecimal.ZERO, null);

        // Adding an identity document moves KYC from NOT_REQUIRED to PENDING,
        // which is exactly the state a regulator asks about on a large purchase.
        customerService.addDocument(world.customerId(), new CustomerDocumentRequest(
                "PASSPORT", "P" + UUID.randomUUID(), null, null, null,
                LocalDate.now().minusYears(2)));

        settledSale(world, new BigDecimal("70.000"));

        var report = complianceService.highValueTransactions(
                LocalDate.now().minusDays(1), LocalDate.now(), world.branchId(),
                new BigDecimal("100000000"));

        assertThat(report.unverifiedCustomerCount()).isEqualTo(1);
        assertThat(report.transactions().get(0).kycVerified()).isFalse();
        assertThat(report.transactions().get(0).kycStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("the KYC report lists a customer awaiting verification as an exception")
    void kycExceptionsAreListed() {
        CommerceFixture.World world = fixture.create(new BigDecimal("1850000"),
                new BigDecimal("45000"), BigDecimal.ZERO, null);
        customerService.addDocument(world.customerId(), new CustomerDocumentRequest(
                "ID_CARD", "ID" + UUID.randomUUID(), null, null, null,
                LocalDate.now().minusYears(1)));

        var report = complianceService.kycStatus();

        assertThat(report.pending()).isPositive();
        assertThat(report.exceptions())
                .anyMatch(e -> e.customerId().equals(world.customerId())
                        && "PENDING".equals(e.kycStatus()));
    }

    @Test
    @DisplayName("inventory is valued at cost and at today's metal rate")
    void inventoryValuationUsesTodaysRate() {
        CommerceFixture.World world = fixture.create(new BigDecimal("1850000"),
                new BigDecimal("45000"), BigDecimal.ZERO, null);
        fixture.availableItem(world, new BigDecimal("10.000"));
        fixture.availableItem(world, new BigDecimal("20.000"));

        var report = reportingService.inventoryValuation(world.branchId());

        // 30 g of 22K at 1,850,000 per gram.
        assertThat(report.itemCount()).isEqualTo(2);
        assertThat(report.totalNetMetalWeight()).isEqualByComparingTo("30.000");
        assertThat(report.totalMetalValueAtToday()).isEqualByComparingTo("55500000.00");
        assertThat(report.byMetal()).hasSize(1);
        assertThat(report.byMetal().get(0).purityCode()).isEqualTo("22K");
    }

    @Test
    @DisplayName("a report range longer than a year is refused")
    void oversizedRangeIsRefused() {
        assertThatThrownBy(() -> reportingService.salesReport(
                LocalDate.now().minusYears(5), LocalDate.now(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("at most 366 days");
    }

    @Test
    @DisplayName("a range that ends before it starts is refused")
    void invertedRangeIsRefused() {
        assertThatThrownBy(() -> reportingService.salesReport(
                LocalDate.now(), LocalDate.now().minusDays(5), null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be before");
    }

    // ---------- helpers ----------

    /** Opens a sale for the world's customer and pays it in full, in cash. */
    private SaleResponse settledSale(CommerceFixture.World world, BigDecimal grossWeight) {
        UUID itemId = fixture.availableItem(world, grossWeight);
        SaleResponse sale = saleService.create(new CreateSaleRequest(
                world.customerId(), world.branchId(), world.locationId(), null, null, null, null,
                null, List.of(new CreateSaleRequest.Line(itemId, null, null))), null);
        paymentService.record(new RecordPaymentRequest(sale.id(), PaymentMethod.CASH,
                sale.amountPayable(), null, null, null, null), "pay-" + UUID.randomUUID());
        return saleService.get(sale.id());
    }
}
