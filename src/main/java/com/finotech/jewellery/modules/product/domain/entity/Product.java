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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The reusable product master (section 17). It is <em>not</em> a stock record:
 * quantity and location live on the serialized {@code JewelleryItem}.
 *
 * <p>Metal and purity are referenced by id rather than by association because
 * they are owned by the metal module.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "product", schema = "product")
public class Product extends BaseEntity {

    @Column(name = "sku", nullable = false, unique = true, length = 50)
    private String sku;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "design_id")
    private JewelleryDesign design;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_type_id", nullable = false)
    private ProductType productType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ProductCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id")
    private Collection collection;

    /** Owned by the metal module; referenced by id to keep module boundaries. */
    @Column(name = "default_metal_id")
    private UUID defaultMetalId;

    @Column(name = "default_purity_id")
    private UUID defaultPurityId;

    @Column(name = "nominal_gross_weight", precision = 12, scale = 3)
    private BigDecimal nominalGrossWeight;

    /** Default making charge parameters; Pricing may override per branch or customer. */
    @Column(name = "default_making_charge_type", length = 20)
    private String defaultMakingChargeType;

    @Column(name = "default_making_charge_value", precision = 15, scale = 4)
    private BigDecimal defaultMakingChargeValue;

    @Column(name = "default_wastage_percentage", precision = 7, scale = 4)
    private BigDecimal defaultWastagePercentage;

    @Column(name = "hsn_code", length = 30)
    private String hsnCode;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MasterStatus status = MasterStatus.ACTIVE;

    @OrderBy("displayOrder asc")
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<ProductImage> images = new ArrayList<>();

    public void addImage(ProductImage image) {
        image.setProduct(this);
        images.add(image);
    }
}
