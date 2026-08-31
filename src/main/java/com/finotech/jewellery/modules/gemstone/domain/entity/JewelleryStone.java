package com.finotech.jewellery.modules.gemstone.domain.entity;

import com.finotech.jewellery.modules.gemstone.domain.enums.StoneSettingType;
import com.finotech.jewellery.modules.gemstone.domain.enums.StoneShape;
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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A stone (or group of identical stones) physically set in one jewellery item.
 *
 * <p>The item is referenced by id rather than by association: the item is owned
 * by the inventory module, and stones must not create a mapping cycle across
 * module boundaries.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "jewellery_stone", schema = "inventory")
public class JewelleryStone extends BaseEntity {

    @Column(name = "jewellery_item_id", nullable = false)
    private UUID jewelleryItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gemstone_id", nullable = false)
    private Gemstone gemstone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_id")
    private StoneCertificate certificate;

    /** Number of identical stones this row represents. */
    @Column(name = "stone_count", nullable = false)
    private int stoneCount = 1;

    /** Total carat weight of all stones in this row. */
    @Column(name = "carat_weight", nullable = false, precision = 10, scale = 3)
    private BigDecimal caratWeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "shape", length = 20)
    private StoneShape shape;

    @Column(name = "cut", length = 30)
    private String cut;

    @Column(name = "colour", length = 30)
    private String colour;

    @Column(name = "clarity", length = 30)
    private String clarity;

    @Enumerated(EnumType.STRING)
    @Column(name = "setting_type", length = 20)
    private StoneSettingType settingType;

    @Column(name = "rate_per_carat", precision = 19, scale = 4)
    private BigDecimal ratePerCarat;

    /** Captured value of this row at the time the item was costed. */
    @Column(name = "stone_value", precision = 19, scale = 4)
    private BigDecimal stoneValue;

    @Column(name = "notes", length = 255)
    private String notes;

    /** Weight in grams, used to derive net metal weight from gross weight. */
    @Column(name = "weight_grams", precision = 12, scale = 3)
    private BigDecimal weightGrams;
}
