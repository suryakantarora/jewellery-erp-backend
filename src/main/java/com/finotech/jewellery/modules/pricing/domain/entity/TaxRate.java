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
 * A tax applied to the sale of jewellery, with an effective window so a rate
 * change never rewrites the tax on documents already issued.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "tax_rate", schema = "sales")
public class TaxRate extends BaseEntity {

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Percentage, e.g. 10.0000 for 10%. */
    @Column(name = "percentage", nullable = false, precision = 7, scale = 4)
    private BigDecimal percentage;

    /** Null applies company-wide. */
    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "product_type_id")
    private UUID productTypeId;

    /**
     * When true the tax is already included in the computed price and is
     * reported for information only, rather than added on top.
     */
    @Column(name = "inclusive", nullable = false)
    private boolean inclusive;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
