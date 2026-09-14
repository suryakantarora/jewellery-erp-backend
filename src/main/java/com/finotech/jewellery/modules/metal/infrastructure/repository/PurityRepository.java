package com.finotech.jewellery.modules.metal.infrastructure.repository;

import com.finotech.jewellery.modules.metal.domain.entity.Purity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurityRepository extends JpaRepository<Purity, UUID> {

    List<Purity> findAllByMetalIdOrderByDisplayOrderAscCodeAsc(UUID metalId);

    @Query("select p from Purity p where p.id = :id and (:companyId is null or p.metal.companyId = :companyId)")
    Optional<Purity> findByIdInCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    boolean existsByMetalIdAndCodeIgnoreCase(UUID metalId, String code);
}
