package com.finotech.jewellery.modules.metal.domain.entity;

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
 * A purity grade of a metal, e.g. 22K gold. {@code fineness} is the fraction of
 * pure metal (22K = 0.9167) and drives conversion between purities.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "purity", schema = "product",
        uniqueConstraints = @UniqueConstraint(name = "uq_purity_metal_code",
                columnNames = {"metal_id", "code"}))
public class Purity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "metal_id", nullable = false)
    private Metal metal;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** Fraction of pure metal, 0 < fineness <= 1. */
    @Column(name = "fineness", nullable = false, precision = 9, scale = 6)
    private BigDecimal fineness;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
