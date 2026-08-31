package com.finotech.jewellery.modules.gemstone.domain.entity;

import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Master list of stone kinds: Diamond, Ruby, Sapphire, Emerald, Pearl, and so
 * on. Diamonds carry the 4C attributes; other stones typically do not, which
 * {@code diamond} distinguishes.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "gemstone", schema = "product")
public class Gemstone extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** True for diamonds, where cut/colour/clarity grading applies. */
    @Column(name = "diamond", nullable = false)
    private boolean diamond;

    @Column(name = "precious", nullable = false)
    private boolean precious;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
