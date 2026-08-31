package com.finotech.jewellery.modules.identity.api.response;

import com.finotech.jewellery.modules.identity.domain.entity.Role;
import java.util.List;
import java.util.UUID;

public record RoleResponse(UUID id,
                           String code,
                           String name,
                           String description,
                           boolean systemRole,
                           boolean superAdmin,
                           List<String> permissions) {

    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription(),
                role.isSystemRole(), role.isSuperAdmin(),
                role.getPermissions().stream().map(p -> p.getCode()).sorted().toList());
    }
}
