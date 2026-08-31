package com.finotech.jewellery.modules.identity.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record RoleRequest(@NotBlank @Size(max = 50) String code,
                          @NotBlank @Size(max = 100) String name,
                          @Size(max = 255) String description,
                          @NotNull Set<UUID> permissionIds) {
}
