package com.finotech.jewellery.modules.product.domain.entity;

import com.finotech.jewellery.modules.product.domain.enums.MasterStatus;
import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Size options per product type, e.g. ring sizes. Kept as master data so POS
 * and e-commerce present the same list.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "size", schema = "product",
        uniqueConstraints = @UniqueConstraint(name = "uq_size_type_code",
                columnNames = {"product_type_id", "code"}))
public class Size extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_type_id", nullable = false)
    private ProductType productType;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "label", nullable = false, length = 50)
    private String label;

    @Column(name = "standard", length = 30)
    private String standard;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MasterStatus status = MasterStatus.ACTIVE;
}
