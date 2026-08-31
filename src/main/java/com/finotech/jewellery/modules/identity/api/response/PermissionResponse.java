package com.finotech.jewellery.modules.identity.api.response;

import com.finotech.jewellery.modules.identity.domain.entity.Permission;
import java.util.UUID;

public record PermissionResponse(UUID id, String code, String module, String description) {

    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getCode(),
                permission.getModule(), permission.getDescription());
    }
}
