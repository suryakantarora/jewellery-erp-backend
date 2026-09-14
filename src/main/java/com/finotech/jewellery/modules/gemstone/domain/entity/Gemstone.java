package com.finotech.jewellery.modules.gemstone.domain.entity;

import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
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
@Table(name = "gemstone", schema = "product",
        uniqueConstraints = @UniqueConstraint(name = "uq_gemstone_company_code",
                columnNames = {"company_id", "code"}))
public class Gemstone extends BaseEntity {

    /**
     * The owning company (tenant). Held as an id, not an association: the
     * organization module owns companies. Never changes after creation.
     */
    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "code", nullable = false, length = 30)
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
