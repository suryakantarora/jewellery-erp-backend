package com.finotech.jewellery.modules.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finotech.jewellery.IntegrationTestBase;
import com.finotech.jewellery.TestSecurity;
import com.finotech.jewellery.modules.crm.api.request.CrmRequests;
import com.finotech.jewellery.modules.crm.application.service.SegmentService;
import com.finotech.jewellery.modules.customer.api.request.CustomerRequest;
import com.finotech.jewellery.modules.organization.api.request.CompanyRequest;
import com.finotech.jewellery.modules.organization.application.service.OrganizationService;
import com.finotech.jewellery.modules.customer.application.service.CustomerService;
import com.finotech.jewellery.modules.loyalty.application.service.LoyaltyService;
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

/**
 * Segments decide who receives a campaign, so a wrong rule means messaging the
 * wrong people. Each criterion is checked against customers built to sit either
 * side of the line.
 */
class SegmentEvaluationIntegrationTest extends IntegrationTestBase {

    @Autowired private SegmentService segmentService;
    @Autowired private CustomerService customerService;
    @Autowired private LoyaltyService loyaltyService;

    @Autowired private OrganizationService organizationService;

    private UUID companyId;

    @BeforeEach
    void authenticate() {
        TestSecurity.authenticateAsSuperAdmin();
        String tag = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        companyId = organizationService.createCompany(new CompanyRequest(
                "CO" + tag, "Test Co", null, null, null, "LAK", null, null, null, null, null)).id();
    }

    @AfterEach
    void clearAuthentication() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("a segment must have at least one criterion")
    void emptySegmentIsRejected() {
        assertThatThrownBy(() -> segmentService.create(new CrmRequests.SegmentRequest(
                unique("EMPTY"), "Everyone", null, null, null, null, null, null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("at least one criterion");
    }

    @Test
    @DisplayName("a birthday-month segment selects only customers born in that month")
    void birthdayMonthSegment() {
        UUID inMarch = customerBornOn(LocalDate.of(1990, 3, 15));
        UUID inJuly = customerBornOn(LocalDate.of(1988, 7, 2));

        UUID segmentId = segmentService.create(new CrmRequests.SegmentRequest(
                unique("MAR"), "March birthdays", null, null, null, null, null, null, 3)).id();

        List<UUID> members = segmentService.evaluate(segmentId);
        assertThat(members).contains(inMarch).doesNotContain(inJuly);
    }

    @Test
    @DisplayName("a tier segment selects only members of that tier")
    void tierSegment() {
        UUID goldCustomer = customerBornOn(LocalDate.of(1991, 4, 1));
        UUID silverCustomer = customerBornOn(LocalDate.of(1992, 4, 1));

        // Enough spend to cross the seeded 5,000-point GOLD threshold.
        loyaltyService.awardForSale(goldCustomer, UUID.randomUUID(),
                new BigDecimal("60000000"), null);
        loyaltyService.enrol(silverCustomer);

        UUID segmentId = segmentService.create(new CrmRequests.SegmentRequest(
                unique("GOLD"), "Gold members", null, null, "GOLD", null, null, null, 4)).id();

        List<UUID> members = segmentService.evaluate(segmentId);
        assertThat(members).contains(goldCustomer).doesNotContain(silverCustomer);
    }

    @Test
    @DisplayName("a customer who never purchased qualifies as lapsed but not as high-value")
    void neverPurchasedIsLapsedNotHighValue() {
        UUID neverPurchased = customerBornOn(LocalDate.of(1995, 5, 20));

        UUID lapsedId = segmentService.create(new CrmRequests.SegmentRequest(
                unique("LAPSED"), "Lapsed", null, null, null, null, null, 90, 5)).id();
        UUID highValueId = segmentService.create(new CrmRequests.SegmentRequest(
                unique("HV"), "High value", null, null, null, new BigDecimal("1000000"),
                null, null, 5)).id();

        assertThat(segmentService.evaluate(lapsedId)).contains(neverPurchased);
        assertThat(segmentService.evaluate(highValueId)).doesNotContain(neverPurchased);
    }

    @Test
    @DisplayName("a spend threshold excludes customers below it")
    void spendThresholdSegment() {
        UUID customer = customerBornOn(LocalDate.of(1993, 6, 10));

        UUID segmentId = segmentService.create(new CrmRequests.SegmentRequest(
                unique("BIG"), "Big spenders", null, null, null,
                new BigDecimal("999999999"), null, null, 6)).id();

        // No sales exist for this customer, so no spend can meet the threshold.
        assertThat(segmentService.evaluate(segmentId)).doesNotContain(customer);
    }

    // ---------- fixtures ----------

    private UUID customerBornOn(LocalDate dateOfBirth) {
        String phone = "+856" + (System.nanoTime() % 1_000_000_000L);
        return customerService.create(new CustomerRequest(null, null, "Segment Test", null,
                phone, null, null, dateOfBirth, null, null, null, null, null, companyId)).id();
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
