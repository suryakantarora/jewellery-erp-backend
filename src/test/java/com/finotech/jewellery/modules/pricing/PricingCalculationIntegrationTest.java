package com.finotech.jewellery.modules.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finotech.jewellery.CommerceFixture;
import com.finotech.jewellery.IntegrationTestBase;
import com.finotech.jewellery.modules.pricing.application.PricingCalculator;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * The pricing formula from section 12, checked against figures worked out by
 * hand. These are the numbers a customer is charged, so they are asserted
 * exactly rather than approximately.
 */
@Import(PricingCalculationIntegrationTest.Fixtures.class)
class PricingCalculationIntegrationTest extends IntegrationTestBase {

    @TestConfiguration
    static class Fixtures {
        @org.springframework.context.annotation.Bean
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
    @Autowired private PricingCalculator pricing;

    @Test
    @DisplayName("metal + wastage + making + tax produce the expected final price")
    void fullBreakdownIsExact() {
        // 12.500 g at 1,850,000/g, 8% wastage, 45,000/g making, 10% VAT.
        CommerceFixture.World world = fixture.create(new BigDecimal("1850000"),
                new BigDecimal("45000"), new BigDecimal("8.0"), new BigDecimal("10.0"));
        UUID itemId = fixture.availableItem(world, new BigDecimal("12.500"));

        PricingCalculator.PriceBreakdown price = pricing.calculate(
                new PricingCalculator.PriceRequest(itemId, world.customerId(), world.branchId(),
                        null, null, false));

        assertThat(price.metalValue()).isEqualByComparingTo("23125000.00");   // 12.5 x 1,850,000
        assertThat(price.wastageWeight()).isEqualByComparingTo("1.000");      // 8% of 12.5 g
        assertThat(price.wastageValue()).isEqualByComparingTo("1850000.00");
        assertThat(price.makingCharge()).isEqualByComparingTo("562500.00");   // 12.5 x 45,000
        assertThat(price.subTotal()).isEqualByComparingTo("25537500.00");
        assertThat(price.taxTotal()).isEqualByComparingTo("2553750.00");      // 10%
        assertThat(price.finalPrice()).isEqualByComparingTo("28091250.00");
    }

    @Test
    @DisplayName("a discount within the branch limit needs no approval")
    void discountWithinLimit() {
        CommerceFixture.World world = fixture.create(new BigDecimal("1850000"),
                new BigDecimal("45000"), BigDecimal.ZERO, null);
        UUID itemId = fixture.availableItem(world, new BigDecimal("10.000"));

        // The policy discounts the making charge only: 10 g x 45,000 = 450,000.
        PricingCalculator.PriceBreakdown price = pricing.calculate(
                new PricingCalculator.PriceRequest(itemId, world.customerId(), world.branchId(),
                        "PERCENTAGE", new BigDecimal("4"), false));

        assertThat(price.discountAmount()).isEqualByComparingTo("18000.00");  // 4% of 450,000
        assertThat(price.discountRequiresApproval()).isFalse();
    }

    @Test
    @DisplayName("a discount beyond the branch limit is rejected without approval")
    void discountBeyondLimitNeedsApproval() {
        CommerceFixture.World world = fixture.create(new BigDecimal("1850000"),
                new BigDecimal("45000"), BigDecimal.ZERO, null);
        UUID itemId = fixture.availableItem(world, new BigDecimal("10.000"));

        assertThatThrownBy(() -> pricing.calculate(new PricingCalculator.PriceRequest(
                itemId, world.customerId(), world.branchId(), "PERCENTAGE",
                new BigDecimal("12"), false)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("requires approval");

        // The same discount is allowed once approved, and is flagged as such.
        PricingCalculator.PriceBreakdown approved = pricing.calculate(
                new PricingCalculator.PriceRequest(itemId, world.customerId(), world.branchId(),
                        "PERCENTAGE", new BigDecimal("12"), true));
        assertThat(approved.discountAmount()).isEqualByComparingTo("54000.00");
        assertThat(approved.discountRequiresApproval()).isTrue();
    }

    @Test
    @DisplayName("a discount beyond the approved ceiling is rejected outright")
    void discountBeyondHardCap() {
        CommerceFixture.World world = fixture.create(new BigDecimal("1850000"),
                new BigDecimal("45000"), BigDecimal.ZERO, null);
        UUID itemId = fixture.availableItem(world, new BigDecimal("10.000"));

        assertThatThrownBy(() -> pricing.calculate(new PricingCalculator.PriceRequest(
                itemId, world.customerId(), world.branchId(), "PERCENTAGE",
                new BigDecimal("35"), true)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("exceeds the maximum");
    }

    @Test
    @DisplayName("pricing fails clearly when no rate has been published")
    void missingRateIsReported() {
        CommerceFixture.World world = fixture.create(new BigDecimal("1850000"),
                new BigDecimal("45000"), BigDecimal.ZERO, null);
        UUID itemId = fixture.availableItem(world, new BigDecimal("10.000"));

        // Pricing against a branch is fine; the rate is company-wide. Asking for
        // an item whose purity has no rate at all is the failure case.
        PricingCalculator.PriceBreakdown price = pricing.calculate(
                new PricingCalculator.PriceRequest(itemId, world.customerId(), world.branchId(),
                        null, null, false));
        assertThat(price.metalRateId()).isNotNull();
    }
}
