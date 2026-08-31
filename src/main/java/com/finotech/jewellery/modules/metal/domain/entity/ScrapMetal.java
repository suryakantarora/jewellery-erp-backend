package com.finotech.jewellery.modules.metal.domain.entity;

import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Loose metal held as scrap: buyback intake, repair filings or melted items.
 * Tracked by pure-metal weight so batches of different purities can be pooled.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "scrap_metal", schema = "inventory")
public class ScrapMetal extends BaseEntity {

    @Column(name = "batch_number", nullable = false, unique = true, length = 50)
    private String batchNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "metal_id", nullable = false)
    private Metal metal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purity_id")
    private Purity purity;

    @Column(name = "gross_weight", nullable = false, precision = 12, scale = 3)
    private BigDecimal grossWeight;

    /** grossWeight × purity fineness, i.e. the recoverable pure metal. */
    @Column(name = "pure_weight", nullable = false, precision = 12, scale = 3)
    private BigDecimal pureWeight;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "source", length = 30)
    private String source;

    @Column(name = "source_reference", length = 100)
    private String sourceReference;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name = "valuation_amount", precision = 19, scale = 4)
    private BigDecimal valuationAmount;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "IN_STOCK";

    @Column(name = "notes", length = 255)
    private String notes;
}
