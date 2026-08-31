package com.finotech.jewellery.modules.organization.application;

import com.finotech.jewellery.modules.organization.domain.enums.LocationType;
import java.util.UUID;

/**
 * The published contract other modules use to reach organization data.
 * Inventory, Sales and Procurement depend on this interface only — they never
 * query organization tables directly (dependency rule 5).
 */
public interface OrganizationDirectory {

    /** @return a lightweight view of a location, or throws if it does not exist */
    LocationView requireLocation(UUID locationId);

    boolean branchExists(UUID branchId);

    /** Stock-holding locations that have a low-stock threshold configured. */
    java.util.List<LocationView> monitoredLocations();

    record LocationView(UUID id,
                        UUID branchId,
                        String code,
                        String name,
                        LocationType type,
                        boolean dualAuthorization,
                        Integer lowStockThreshold,
                        boolean active) {

        public boolean canHoldStock() {
            return active && type.isStockHolding();
        }
    }
}
