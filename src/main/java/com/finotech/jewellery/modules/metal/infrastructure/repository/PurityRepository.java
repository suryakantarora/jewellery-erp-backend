package com.finotech.jewellery.modules.metal.infrastructure.repository;

import com.finotech.jewellery.modules.metal.domain.entity.Purity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurityRepository extends JpaRepository<Purity, UUID> {

    List<Purity> findAllByMetalIdOrderByDisplayOrderAscCodeAsc(UUID metalId);

    boolean existsByMetalIdAndCodeIgnoreCase(UUID metalId, String code);
}
