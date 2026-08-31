package com.finotech.jewellery.modules.identity.domain.entity;

import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single grantable capability, e.g. {@code INVENTORY_TRANSFER}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "permission", schema = "identity")
public class Permission extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    @Column(name = "module", nullable = false, length = 50)
    private String module;

    @Column(name = "description", length = 255)
    private String description;
}
