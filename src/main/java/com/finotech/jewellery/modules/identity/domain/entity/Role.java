package com.finotech.jewellery.modules.identity.domain.entity;

import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A named bundle of permissions. {@code systemRole} rows are seeded and cannot
 * be deleted.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "role", schema = "identity")
public class Role extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @Column(name = "super_admin", nullable = false)
    private boolean superAdmin;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "role_permission", schema = "identity",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new LinkedHashSet<>();

    public void replacePermissions(Set<Permission> newPermissions) {
        permissions.clear();
        permissions.addAll(newPermissions);
    }
}
