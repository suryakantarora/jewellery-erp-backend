package com.finotech.jewellery.modules.warehouse.infrastructure.repository;

import com.finotech.jewellery.modules.warehouse.domain.entity.StorageBin;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageBinRepository extends JpaRepository<StorageBin, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    List<StorageBin> findAllByLocationIdOrderByCodeAsc(UUID locationId);

    boolean existsByParentId(UUID parentId);
}
