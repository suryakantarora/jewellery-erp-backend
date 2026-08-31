package com.finotech.jewellery.modules.pricing.domain.entity;

import com.finotech.jewellery.modules.pricing.domain.enums.ChargeType;
import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A making-charge and wastage rule. Rules are matched from most specific to
 * least: a rule naming a product beats one naming a product type, which beats a
 * catch-all. {@code priority} breaks ties, highest first.
 *
 * <p>Any null scope field means "applies to all", so one row can cover a whole
 * category without enumerating it.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "making_charge_rule", schema = "sales")
public class MakingChargeRule extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    // ---------- scope (null = any) ----------

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "product_type_id")
    private UUID productTypeId;

    @Column(name = "metal_id")
    private UUID metalId;

    @Column(name = "purity_id")
    private UUID purityId;

    @Column(name = "branch_id")
    private UUID branchId;

    // ---------- charge ----------

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_type", nullable = false, length = 30)
    private ChargeType chargeType;

    @Column(name = "charge_value", nullable = false, precision = 15, scale = 4)
    private BigDecimal chargeValue;

    /** Percentage of net metal weight added as wastage, e.g. 8.0000 for 8%. */
    @Column(name = "wastage_percentage", precision = 7, scale = 4)
    private BigDecimal wastagePercentage;

    @Column(name = "min_charge", precision = 19, scale = 4)
    private BigDecimal minCharge;

    @Column(name = "max_charge", precision = 19, scale = 4)
    private BigDecimal maxCharge;

    // ---------- validity ----------

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** How tightly this rule is scoped; higher wins when several rules match. */
    public int specificity() {
        int score = 0;
        if (productId != null) {
            score += 16;
        }
        if (purityId != null) {
            score += 8;
        }
        if (productTypeId != null) {
            score += 4;
        }
        if (metalId != null) {
            score += 2;
        }
        if (branchId != null) {
            score += 1;
        }
        return score;
    }
}
