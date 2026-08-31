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

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "quotation_line", schema = "sales")
public class QuotationLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    @Column(name = "jewellery_item_id", nullable = false)
    private UUID jewelleryItemId;

    @Column(name = "item_code", nullable = false, length = 50)
    private String itemCode;

    @Column(name = "metal_value", precision = 19, scale = 4)
    private BigDecimal metalValue;

    @Column(name = "making_charge", precision = 19, scale = 4)
    private BigDecimal makingCharge;

    @Column(name = "wastage_value", precision = 19, scale = 4)
    private BigDecimal wastageValue;

    @Column(name = "stone_value", precision = 19, scale = 4)
    private BigDecimal stoneValue;

    @Column(name = "discount_amount", precision = 19, scale = 4)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineTotal = BigDecimal.ZERO;
}
