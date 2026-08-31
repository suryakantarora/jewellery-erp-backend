package com.finotech.jewellery.modules.loyalty.domain.entity;

import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The rules a customer earns and spends points under.
 *
 * <p>Only one program is active at a time per company; keeping it as an entity
 * rather than configuration means the earn rate can change without a
 * deployment, and historical transactions still reference the program that
 * produced them.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "loyalty_program", schema = "crm")
public class LoyaltyProgram extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "company_id")
    private UUID companyId;

    /** Points earned per unit of currency spent, e.g. 0.0001 = 1 point per 10,000. */
    @Column(name = "points_per_currency_unit", nullable = false, precision = 15, scale = 8)
    private BigDecimal pointsPerCurrencyUnit;

    /** Money a single point is worth when redeemed. */
    @Column(name = "currency_value_per_point", nullable = false, precision = 15, scale = 4)
    private BigDecimal currencyValuePerPoint;

    /** Points earned expire this many months after being awarded; null never expires. */
    @Column(name = "points_validity_months")
    private Integer pointsValidityMonths;

    @Column(name = "minimum_redeemable_points")
    private Integer minimumRedeemablePoints;

    /**
     * Only the making charge is usually eligible for points, since metal value
     * is close to cost. Null means the whole sale earns.
     */
    @Column(name = "earn_on_making_charge_only", nullable = false)
    private boolean earnOnMakingChargeOnly;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OrderBy("minimumPoints asc")
    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<LoyaltyTier> tiers = new ArrayList<>();

    public void addTier(LoyaltyTier tier) {
        tier.setProgram(this);
        tiers.add(tier);
    }

    /** The highest tier whose threshold the given lifetime points reach. */
    public LoyaltyTier tierFor(long lifetimePoints) {
        LoyaltyTier match = null;
        for (LoyaltyTier tier : tiers) {
            if (lifetimePoints >= tier.getMinimumPoints()) {
                match = tier;
            }
        }
        return match;
    }
}
