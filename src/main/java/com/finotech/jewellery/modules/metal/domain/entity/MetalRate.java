package com.finotech.jewellery.modules.metal.domain.entity;

import com.finotech.jewellery.modules.metal.domain.enums.RateType;
import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A published rate per unit weight for one metal/purity on one day.
 *
 * <p>Rates are append-only history: a sale records the rate it used, so
 * republishing tomorrow never changes yesterday's prices (section 17).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "metal_rate", schema = "product")
public class MetalRate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "metal_id", nullable = false)
    private Metal metal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purity_id", nullable = false)
    private Purity purity;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", nullable = false, length = 20)
    private RateType rateType = RateType.SELLING;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    /** Price of one unit weight (the metal's weightUnit) in the given currency. */
    @Column(name = "rate_per_unit", nullable = false, precision = 19, scale = 4)
    private BigDecimal ratePerUnit;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "LAK";

    /** Null means the rate applies company-wide. */
    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "published_by", length = 100)
    private String publishedBy;

    @Column(name = "notes", length = 255)
    private String notes;
}
