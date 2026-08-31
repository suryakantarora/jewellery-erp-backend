package com.finotech.jewellery.modules.metal.infrastructure.repository;

import com.finotech.jewellery.modules.metal.domain.entity.Metal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetalRepository extends JpaRepository<Metal, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    List<Metal> findAllByOrderByNameAsc();
}
