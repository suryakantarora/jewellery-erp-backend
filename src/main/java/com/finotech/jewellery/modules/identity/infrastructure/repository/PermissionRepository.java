package com.finotech.jewellery.modules.identity.infrastructure.repository;

import com.finotech.jewellery.modules.identity.domain.entity.Permission;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    List<Permission> findAllByOrderByModuleAscCodeAsc();

    Set<Permission> findAllByIdIn(Set<UUID> ids);
}
