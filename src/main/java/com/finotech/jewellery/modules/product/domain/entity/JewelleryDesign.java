package com.finotech.jewellery.modules.product.domain.entity;

import com.finotech.jewellery.modules.product.domain.enums.MasterStatus;
import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The artistic/technical definition a physical item is made from. Several
 * products (different metals or purities) can share one design.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "jewellery_design", schema = "product")
public class JewelleryDesign extends BaseEntity {

    @Column(name = "design_code", nullable = false, unique = true, length = 50)
    private String designCode;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_type_id")
    private ProductType productType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id")
    private Collection collection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @Column(name = "designer", length = 150)
    private String designer;

    /** Indicative weight used for planning; the physical item carries the real one. */
    @Column(name = "nominal_gross_weight", precision = 12, scale = 3)
    private BigDecimal nominalGrossWeight;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MasterStatus status = MasterStatus.ACTIVE;

    /**
     * Catalogue artwork, ordered for display. Batched so a page of designs
     * reads all its primary keys in one query rather than one per row.
     */
    @OrderBy("displayOrder asc, createdAt asc")
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "design", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<DesignImage> images = new ArrayList<>();
}
