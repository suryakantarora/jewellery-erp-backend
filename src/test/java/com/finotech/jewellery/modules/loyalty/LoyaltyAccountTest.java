package com.finotech.jewellery.modules.loyalty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finotech.jewellery.modules.loyalty.domain.entity.LoyaltyAccount;
import com.finotech.jewellery.modules.loyalty.domain.entity.LoyaltyProgram;
import com.finotech.jewellery.modules.loyalty.domain.entity.LoyaltyTier;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Point arithmetic is money in all but name, so the balance rules are asserted
 * directly rather than only through the service.
 */
class LoyaltyAccountTest {

    @Test
    @DisplayName("a balance can never go negative")
    void balanceCannotGoNegative() {
        LoyaltyAccount account = account();
        account.earn(100);

        assertThatThrownBy(() -> account.redeem(101))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Insufficient points");
        assertThat(account.getPointsBalance()).isEqualTo(100);

        assertThatThrownBy(() -> account.adjust(-500))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("below zero");
    }

    @Test
    @DisplayName("spending points does not reduce lifetime points, so it cannot demote a tier")
    void redeemingDoesNotAffectLifetimePoints() {
        LoyaltyAccount account = account();
        account.earn(6000);
        account.redeem(5500);

        assertThat(account.getPointsBalance()).isEqualTo(500);
        assertThat(account.getLifetimePoints()).isEqualTo(6000);
        assertThat(account.getPointsRedeemed()).isEqualTo(5500);
    }

    @Test
    @DisplayName("reversing an award reduces lifetime points, because they were never earned")
    void reversalReducesLifetimePoints() {
        LoyaltyAccount account = account();
        account.earn(1000);
        account.reverse(1000);

        assertThat(account.getPointsBalance()).isZero();
        assertThat(account.getLifetimePoints()).isZero();
    }

    @Test
    @DisplayName("a reversal cannot push the balance negative when points were already spent")
    void reversalIsCappedAtTheBalance() {
        LoyaltyAccount account = account();
        account.earn(1000);
        account.redeem(800);

        account.reverse(1000);

        // Only the 200 still held can be taken back; the balance stops at zero.
        assertThat(account.getPointsBalance()).isZero();
        assertThat(account.getLifetimePoints()).isZero();
    }

    @Test
    @DisplayName("earning and redeeming must be positive amounts")
    void movementsMustBePositive() {
        LoyaltyAccount account = account();
        assertThatThrownBy(() -> account.earn(0)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> account.earn(-5)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> account.redeem(0)).isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("the tier is the highest threshold the lifetime points reach")
    void tierIsResolvedByThreshold() {
        LoyaltyProgram program = program();

        assertThat(program.tierFor(0).getCode()).isEqualTo("SILVER");
        assertThat(program.tierFor(4999).getCode()).isEqualTo("SILVER");
        assertThat(program.tierFor(5000).getCode()).isEqualTo("GOLD");
        assertThat(program.tierFor(19999).getCode()).isEqualTo("GOLD");
        assertThat(program.tierFor(20000).getCode()).isEqualTo("PLATINUM");
        assertThat(program.tierFor(1_000_000).getCode()).isEqualTo("PLATINUM");
    }

    // ---------- fixtures ----------

    private LoyaltyAccount account() {
        LoyaltyAccount account = new LoyaltyAccount();
        account.setProgram(program());
        return account;
    }

    private LoyaltyProgram program() {
        LoyaltyProgram program = new LoyaltyProgram();
        program.setCode("STANDARD");
        program.setPointsPerCurrencyUnit(new BigDecimal("0.0001"));
        program.setCurrencyValuePerPoint(new BigDecimal("100"));
        program.addTier(tier("SILVER", 0, "1.0"));
        program.addTier(tier("GOLD", 5000, "1.25"));
        program.addTier(tier("PLATINUM", 20000, "1.5"));
        return program;
    }

    private LoyaltyTier tier(String code, long minimum, String multiplier) {
        LoyaltyTier tier = new LoyaltyTier();
        tier.setCode(code);
        tier.setName(code);
        tier.setMinimumPoints(minimum);
        tier.setEarnMultiplier(new BigDecimal(multiplier));
        return tier;
    }
}
