package com.finotech.jewellery.modules.loyalty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finotech.jewellery.IntegrationTestBase;
import com.finotech.jewellery.TestSecurity;
import com.finotech.jewellery.modules.customer.api.request.CustomerRequest;
import com.finotech.jewellery.modules.customer.application.service.CustomerService;
import com.finotech.jewellery.modules.loyalty.api.request.LoyaltyRequests;
import com.finotech.jewellery.modules.loyalty.application.service.LoyaltyService;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Earning is driven by an event and must be safe to replay: a redelivered
 * message must not credit a customer twice.
 */
class LoyaltyEarningIntegrationTest extends IntegrationTestBase {

    @Autowired private LoyaltyService loyaltyService;
    @Autowired private CustomerService customerService;

    @BeforeEach
    void authenticate() {
        TestSecurity.authenticateAsSuperAdmin();
    }

    @AfterEach
    void clearAuthentication() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("the same sale cannot award points twice")
    void awardingIsIdempotentPerSale() {
        UUID customerId = newCustomer();
        UUID saleId = UUID.randomUUID();

        long first = loyaltyService.awardForSale(customerId, saleId,
                new BigDecimal("23687500"), null);
        long replay = loyaltyService.awardForSale(customerId, saleId,
                new BigDecimal("23687500"), null);

        // 23,687,500 x 0.0001 = 2368.75, truncated to whole points.
        assertThat(first).isEqualTo(2368);
        assertThat(replay).as("a replayed event must award nothing").isZero();
        assertThat(loyaltyService.getAccount(customerId).pointsBalance()).isEqualTo(2368);
    }

    @Test
    @DisplayName("crossing a threshold promotes the tier, and the multiplier applies next time")
    void tierPromotionAppliesToLaterEarnings() {
        UUID customerId = newCustomer();

        // Two purchases at the base rate take the customer past the 5,000 GOLD line.
        loyaltyService.awardForSale(customerId, UUID.randomUUID(), new BigDecimal("30000000"), null);
        assertThat(loyaltyService.getAccount(customerId).tierCode()).isEqualTo("SILVER");

        loyaltyService.awardForSale(customerId, UUID.randomUUID(), new BigDecimal("30000000"), null);
        assertThat(loyaltyService.getAccount(customerId).tierCode()).isEqualTo("GOLD");

        // The next purchase earns at the GOLD multiplier of 1.25.
        long asGold = loyaltyService.awardForSale(customerId, UUID.randomUUID(),
                new BigDecimal("10000000"), null);
        assertThat(asGold).isEqualTo(1250);
    }

    @Test
    @DisplayName("returning a sale reverses the points it earned")
    void returningASaleReversesPoints() {
        UUID customerId = newCustomer();
        UUID saleId = UUID.randomUUID();
        loyaltyService.awardForSale(customerId, saleId, new BigDecimal("20000000"), null);
        assertThat(loyaltyService.getAccount(customerId).pointsBalance()).isEqualTo(2000);

        long reversed = loyaltyService.reverseForSale(saleId);

        assertThat(reversed).isEqualTo(2000);
        var account = loyaltyService.getAccount(customerId);
        assertThat(account.pointsBalance()).isZero();
        assertThat(account.lifetimePoints()).isZero();
    }

    @Test
    @DisplayName("enrolling twice returns the same account")
    void enrolmentIsIdempotent() {
        UUID customerId = newCustomer();
        var first = loyaltyService.enrol(customerId);
        var second = loyaltyService.enrol(customerId);
        assertThat(second.id()).isEqualTo(first.id());
    }

    @Test
    @DisplayName("a redemption below the program minimum is refused")
    void redemptionRespectsMinimum() {
        UUID customerId = newCustomer();
        loyaltyService.awardForSale(customerId, UUID.randomUUID(), new BigDecimal("20000000"), null);

        assertThatThrownBy(() -> loyaltyService.redeem(new LoyaltyRequests.RedeemRequest(
                customerId, 50, null, null, "Too small")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("At least 100 points");

        // The seeded program values a point at 100, so 500 points are worth 50,000.
        var after = loyaltyService.redeem(new LoyaltyRequests.RedeemRequest(
                customerId, 500, null, null, "Discount"));
        assertThat(after.pointsBalance()).isEqualTo(1500);
        assertThat(after.redeemableValue()).isEqualByComparingTo("150000.00");
    }

    @Test
    @DisplayName("a sale that earns less than one point awards nothing rather than rounding up")
    void subPointPurchaseEarnsNothing() {
        UUID customerId = newCustomer();
        // 5,000 x 0.0001 = 0.5 points.
        long points = loyaltyService.awardForSale(customerId, UUID.randomUUID(),
                new BigDecimal("5000"), null);
        assertThat(points).isZero();
    }

    private UUID newCustomer() {
        String phone = "+856" + (System.nanoTime() % 1_000_000_000L);
        return customerService.create(new CustomerRequest(null, null, "Loyalty Test", null,
                phone, null, null, null, null, null, null, null, null)).id();
    }
}
