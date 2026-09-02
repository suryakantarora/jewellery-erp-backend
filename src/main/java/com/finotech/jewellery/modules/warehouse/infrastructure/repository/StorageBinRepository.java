package com.finotech.jewellery.modules.warehouse.infrastructure.repository;

import com.finotech.jewellery.modules.warehouse.domain.entity.StorageBin;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageBinRepository extends JpaRepository<StorageBin, UUID> {

    /**
     * Scoped to the location, matching the {@code (location_id, code)}
     * constraint. A global check would reject a second vault's TRAY-1 with a
     * "code already exists" that named a bin in another branch entirely.
     */
    boolean existsByLocationIdAndCodeIgnoreCase(UUID locationId, String code);

    List<StorageBin> findAllByLocationIdOrderByCodeAsc(UUID locationId);

    boolean existsByParentId(UUID parentId);
}
