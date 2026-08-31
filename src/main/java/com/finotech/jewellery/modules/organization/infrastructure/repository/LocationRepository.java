package com.finotech.jewellery.modules.organization.infrastructure.repository;

import com.finotech.jewellery.modules.organization.domain.entity.Location;
import com.finotech.jewellery.modules.organization.domain.enums.LocationType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    List<Location> findAllByBranchId(UUID branchId);

    List<Location> findAllByBranchIdAndType(UUID branchId, LocationType type);

    boolean existsByParentId(UUID parentId);

    /** Active stock-holding locations with a low-stock threshold set. */
    @org.springframework.data.jpa.repository.Query("""
            select l from Location l
            where l.status = com.finotech.jewellery.modules.organization.domain.enums.OrganizationStatus.ACTIVE
              and l.lowStockThreshold is not null
            """)
    List<Location> findMonitored();
}
