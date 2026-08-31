package com.finotech.jewellery.modules.sales.domain.entity;

import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One item sold, with the full price breakdown frozen at the time of sale. The
 * metal rate used is recorded so an invoice can always be reproduced exactly.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "sale_line", schema = "sales")
public class SaleLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @Column(name = "jewellery_item_id", nullable = false)
    private UUID jewelleryItemId;

    @Column(name = "item_code", nullable = false, length = 50)
    private String itemCode;

    // ---- frozen price breakdown ----

    @Column(name = "metal_rate_id")
    private UUID metalRateId;

    @Column(name = "metal_rate_per_unit", precision = 19, scale = 4)
    private BigDecimal metalRatePerUnit;

    @Column(name = "net_metal_weight", precision = 12, scale = 3)
    private BigDecimal netMetalWeight;

    @Column(name = "metal_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal metalValue = BigDecimal.ZERO;

    @Column(name = "wastage_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal wastageValue = BigDecimal.ZERO;

    @Column(name = "making_charge", nullable = false, precision = 19, scale = 4)
    private BigDecimal makingCharge = BigDecimal.ZERO;

    @Column(name = "stone_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal stoneValue = BigDecimal.ZERO;

    /** Standing entitlement from the customer's loyalty tier at the time of sale. */
    @Column(name = "tier_discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal tierDiscountAmount = BigDecimal.ZERO;

    @Column(name = "loyalty_tier_code", length = 30)
    private String loyaltyTierCode;

    /** Total taken off: tier entitlement plus any staff-requested discount. */
    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Column(name = "returned", nullable = false)
    private boolean returned;

    @Column(name = "return_reason", length = 500)
    private String returnReason;
}
