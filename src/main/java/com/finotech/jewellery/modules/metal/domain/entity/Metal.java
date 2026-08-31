package com.finotech.jewellery.modules.metal.domain.entity;

import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Gold, Silver, Platinum, White Gold, and so on.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "metal", schema = "product")
public class Metal extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "symbol", length = 10)
    private String symbol;

    /** Unit every rate and weight for this metal is expressed in, e.g. GRAM. */
    @Column(name = "weight_unit", nullable = false, length = 10)
    private String weightUnit = "GRAM";

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
