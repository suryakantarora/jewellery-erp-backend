package com.finotech.jewellery.modules.gemstone.infrastructure.repository;

import com.finotech.jewellery.modules.gemstone.domain.entity.Gemstone;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GemstoneRepository extends JpaRepository<Gemstone, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    List<Gemstone> findAllByOrderByNameAsc();
}
