package com.finotech.jewellery.modules.loyalty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finotech.jewellery.CommerceFixture;
import com.finotech.jewellery.IntegrationTestBase;
import com.finotech.jewellery.TestSecurity;
import com.finotech.jewellery.modules.loyalty.api.request.LoyaltyRequests;
import com.finotech.jewellery.modules.loyalty.application.service.LoyaltyService;
import com.finotech.jewellery.modules.payment.api.request.RecordPaymentRequest;
import com.finotech.jewellery.modules.payment.application.service.PaymentService;
import com.finotech.jewellery.modules.payment.domain.enums.PaymentMethod;
import com.finotech.jewellery.modules.pricing.application.PricingCalculator;
import com.finotech.jewellery.modules.sales.api.request.CreateSaleRequest;
import com.finotech.jewellery.modules.sales.api.response.SaleResponse;
import com.finotech.jewellery.modules.sales.application.service.SaleService;
import com.finotech.jewellery.modules.sales.domain.enums.SaleStatus;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.math.BigDecimal;
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
 * Loyalty benefits reaching the price the customer actually pays: the tier
 * entitlement applied by Pricing, and points spent against a sale.
 */
@Import(LoyaltyRedemptionOnSaleIntegrationTest.Fixtures.class)
class LoyaltyRedemptionOnSaleIntegrationTest extends IntegrationTestBase {

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
    @Autowired private LoyaltyService loyaltyService;
    @Autowired private PricingCalculator pricing;

    @BeforeEach
    void authenticate() {
        TestSecurity.authenticateAsSuperAdmin();
    }

    @AfterEach
    void clearAuthentication() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("a GOLD customer's tier entitlement comes off the price automatically")
    void tierDiscountIsApplied() {
        CommerceFixture.World world = world();
        UUID itemId = fixture.availableItem(world, new BigDecimal("12.500"));
        promoteToGold(world.customerId());

        PricingCalculator.PriceBreakdown price = pricing.calculate(
                new PricingCalculator.PriceRequest(itemId, world.customerId(), world.branchId(),
                        null, null, false));

        // Sub total 23,687,500; the seeded GOLD tier grants 2%.
        assertThat(price.loyaltyTierCode()).isEqualTo("GOLD");
        assertThat(price.subTotal()).isEqualByComparingTo("23687500.00");
        assertThat(price.tierDiscountAmount()).isEqualByComparingTo("473750.00");
        assertThat(price.finalPrice()).isEqualByComparingTo("23213750.00");
        // A tier entitlement is not a staff decision, so it needs no approval.
        assertThat(price.discountRequiresApproval()).isFalse();
    }

    @Test
    @DisplayName("pricing without a customer returns the list price")
    void listPriceHasNoTierDiscount() {
        CommerceFixture.World world = world();
        UUID itemId = fixture.availableItem(world, new BigDecimal("12.500"));
        promoteToGold(world.customerId());

        PricingCalculator.PriceBreakdown price = pricing.calculate(
                new PricingCalculator.PriceRequest(itemId, null, world.branchId(),
                        null, null, false));

        assertThat(price.tierDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(price.finalPrice()).isEqualByComparingTo("23687500.00");
    }

    @Test
    @DisplayName("redeemed points reduce what the customer pays, not the sale total")
    void redemptionReducesAmountPayable() {
        CommerceFixture.World world = world();
        UUID itemId = fixture.availableItem(world, new BigDecimal("12.500"));
        loyaltyService.enrol(world.customerId());
        loyaltyService.adjust(new LoyaltyRequests.AdjustRequest(
                world.customerId(), 5000, "Test balance"));

        SaleResponse sale = saleService.create(new CreateSaleRequest(
                world.customerId(), world.branchId(), world.locationId(), null, null, null,
                5000L, null, List.of(new CreateSaleRequest.Line(itemId, null, null))), null);

        // 5,000 points at the seeded value of 100 each.
        assertThat(sale.loyaltyPointsRedeemed()).isEqualTo(5000);
        assertThat(sale.loyaltyRedemptionValue()).isEqualByComparingTo("500000.00");
        assertThat(sale.amountPayable())
                .isEqualByComparingTo(sale.totalAmount().subtract(new BigDecimal("500000.00")));
        assertThat(loyaltyService.getAccount(world.customerId()).pointsBalance()).isZero();

        // Paying the reduced amount settles the sale in full.
        paymentService.record(new RecordPaymentRequest(sale.id(), PaymentMethod.CASH,
                sale.amountPayable(), null, null, null, null), "pay-" + UUID.randomUUID());
        assertThat(saleService.get(sale.id()).status()).isEqualTo(SaleStatus.CONFIRMED);
    }

    @Test
    @DisplayName("cancelling a sale returns the points without inflating lifetime points")
    void cancellingReturnsPointsWithoutPromoting() {
        CommerceFixture.World world = world();
        UUID itemId = fixture.availableItem(world, new BigDecimal("12.500"));
        loyaltyService.enrol(world.customerId());
        loyaltyService.adjust(new LoyaltyRequests.AdjustRequest(
                world.customerId(), 3000, "Test balance"));
        long lifetimeBefore = loyaltyService.getAccount(world.customerId()).lifetimePoints();

        SaleResponse sale = saleService.create(new CreateSaleRequest(
                world.customerId(), world.branchId(), world.locationId(), null, null, null,
                2000L, null, List.of(new CreateSaleRequest.Line(itemId, null, null))), null);
        assertThat(loyaltyService.getAccount(world.customerId()).pointsBalance()).isEqualTo(1000);

        saleService.cancel(sale.id(), "Customer changed their mind");

        var account = loyaltyService.getAccount(world.customerId());
        assertThat(account.pointsBalance()).as("points come back").isEqualTo(3000);
        assertThat(account.lifetimePoints())
                .as("lifetime points must not move, or the customer is promoted for nothing")
                .isEqualTo(lifetimeBefore);
    }

    @Test
    @DisplayName("redeeming more points than the customer holds fails and opens no sale")
    void overRedemptionFailsCleanly() {
        CommerceFixture.World world = world();
        UUID itemId = fixture.availableItem(world, new BigDecimal("12.500"));
        loyaltyService.enrol(world.customerId());

        assertThatThrownBy(() -> saleService.create(new CreateSaleRequest(
                world.customerId(), world.branchId(), world.locationId(), null, null, null,
                999_999L, null, List.of(new CreateSaleRequest.Line(itemId, null, null))), null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Insufficient points");
    }

    // ---------- fixtures ----------

    private CommerceFixture.World world() {
        return fixture.create(new BigDecimal("1850000"), new BigDecimal("45000"),
                BigDecimal.ZERO, null);
    }

    /** The seeded GOLD tier starts at 5,000 lifetime points and grants 2%. */
    private void promoteToGold(UUID customerId) {
        loyaltyService.enrol(customerId);
        loyaltyService.adjust(new LoyaltyRequests.AdjustRequest(
                customerId, 8000, "Promote for test"));
        assertThat(loyaltyService.getAccount(customerId).tierCode()).isEqualTo("GOLD");
    }
}
