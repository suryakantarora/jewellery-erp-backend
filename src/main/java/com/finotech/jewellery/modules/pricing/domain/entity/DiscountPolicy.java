package com.finotech.jewellery.modules.pricing.domain.entity;

import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The discount a branch may give without escalation. A request beyond
 * {@code maxPercentageWithoutApproval} needs DISCOUNT_APPROVE, which is how the
 * platform keeps margin decisions visible.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "discount_policy", schema = "sales")
public class DiscountPolicy extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "max_percentage_without_approval", nullable = false, precision = 7, scale = 4)
    private BigDecimal maxPercentageWithoutApproval = BigDecimal.ZERO;

    @Column(name = "max_percentage_with_approval", nullable = false, precision = 7, scale = 4)
    private BigDecimal maxPercentageWithApproval = BigDecimal.ZERO;

    /** Whether a discount may reduce the making charge, the metal value, or both. */
    @Column(name = "applies_to_making_charge", nullable = false)
    private boolean appliesToMakingCharge = true;

    @Column(name = "applies_to_metal_value", nullable = false)
    private boolean appliesToMetalValue;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
