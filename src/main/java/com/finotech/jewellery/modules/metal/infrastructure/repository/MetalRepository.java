package com.finotech.jewellery.modules.metal.infrastructure.repository;

import com.finotech.jewellery.modules.metal.domain.entity.Metal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MetalRepository extends JpaRepository<Metal, UUID> {

    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);

    @Query("select m from Metal m where (:companyId is null or m.companyId = :companyId) order by m.name asc")
    List<Metal> findAllInCompany(@Param("companyId") UUID companyId);

    @Query("select m from Metal m where m.id = :id and (:companyId is null or m.companyId = :companyId)")
    Optional<Metal> findByIdInCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);
}
