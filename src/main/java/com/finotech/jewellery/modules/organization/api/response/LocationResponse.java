package com.finotech.jewellery.modules.organization.api.response;

import com.finotech.jewellery.modules.organization.domain.entity.Location;
import com.finotech.jewellery.modules.organization.domain.enums.LocationType;
import com.finotech.jewellery.modules.organization.domain.enums.OrganizationStatus;
import java.util.UUID;

public record LocationResponse(UUID id, UUID branchId, UUID parentId, String code, String name,
                               LocationType type, boolean dualAuthorization, Integer lowStockThreshold,
                               String description,
                               OrganizationStatus status) {

    public static LocationResponse from(Location l) {
        return new LocationResponse(l.getId(), l.getBranch().getId(),
                l.getParent() == null ? null : l.getParent().getId(),
                l.getCode(), l.getName(), l.getType(), l.isDualAuthorization(), l.getLowStockThreshold(),
                l.getDescription(), l.getStatus());
    }
}
