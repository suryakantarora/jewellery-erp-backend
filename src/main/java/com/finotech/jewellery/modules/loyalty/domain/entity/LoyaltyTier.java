package com.finotech.jewellery.modules.loyalty.domain.entity;

import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A tier such as Silver, Gold or Platinum, reached at a lifetime-points
 * threshold and carrying an earn multiplier.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "loyalty_tier", schema = "crm",
        uniqueConstraints = @UniqueConstraint(name = "uq_tier_program_code",
                columnNames = {"program_id", "code"}))
public class LoyaltyTier extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private LoyaltyProgram program;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Lifetime points needed to reach this tier. */
    @Column(name = "minimum_points", nullable = false)
    private long minimumPoints;

    /** Applied to points earned, e.g. 1.5000 for a 50% bonus. */
    @Column(name = "earn_multiplier", nullable = false, precision = 7, scale = 4)
    private BigDecimal earnMultiplier = BigDecimal.ONE;

    /** Standing discount percentage this tier grants, if any. */
    @Column(name = "discount_percentage", precision = 7, scale = 4)
    private BigDecimal discountPercentage;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "benefits", length = 1000)
    private String benefits;
}
