package com.finotech.jewellery.modules.organization.api.request;

import com.finotech.jewellery.modules.organization.domain.enums.LocationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record LocationRequest(@NotNull UUID branchId,
                              UUID parentId,
                              @NotBlank @Size(max = 40) String code,
                              @NotBlank @Size(max = 150) String name,
                              @NotNull LocationType type,
                              boolean dualAuthorization,
                              @jakarta.validation.constraints.PositiveOrZero Integer lowStockThreshold,
                              @Size(max = 255) String description) {
}
