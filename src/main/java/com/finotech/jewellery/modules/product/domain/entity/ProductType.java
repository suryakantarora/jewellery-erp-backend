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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The kind of article: Ring, Necklace, Bracelet, Earring, Pendant, Bangle,
 * Chain. Drives which attributes (e.g. size) are relevant.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "product_type", schema = "product")
public class ProductType extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ProductCategory category;

    /** Rings and bangles are sized; chains and pendants usually are not. */
    @Column(name = "sizeable", nullable = false)
    private boolean sizeable;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MasterStatus status = MasterStatus.ACTIVE;
}
