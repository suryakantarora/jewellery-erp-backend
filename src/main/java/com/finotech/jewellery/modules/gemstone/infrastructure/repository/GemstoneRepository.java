package com.finotech.jewellery.modules.gemstone.infrastructure.repository;

import com.finotech.jewellery.modules.gemstone.domain.entity.Gemstone;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GemstoneRepository extends JpaRepository<Gemstone, UUID> {

    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);

    @Query("select g from Gemstone g where (:companyId is null or g.companyId = :companyId) order by g.name asc")
    List<Gemstone> findAllInCompany(@Param("companyId") UUID companyId);

    @Query("select g from Gemstone g where g.id = :id and (:companyId is null or g.companyId = :companyId)")
    Optional<Gemstone> findByIdInCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);
}
