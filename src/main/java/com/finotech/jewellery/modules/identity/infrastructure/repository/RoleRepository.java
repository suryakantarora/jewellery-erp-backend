package com.finotech.jewellery.modules.identity.infrastructure.repository;

import com.finotech.jewellery.modules.identity.domain.entity.Role;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findWithPermissionsById(UUID id);

    Optional<Role> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    Set<Role> findAllByIdIn(Set<UUID> ids);
}
