package com.finotech.jewellery.modules.product.infrastructure.repository;

import com.finotech.jewellery.modules.product.domain.entity.JewelleryDesign;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface JewelleryDesignRepository extends JpaRepository<JewelleryDesign, UUID> {

    boolean existsByCompanyIdAndDesignCodeIgnoreCase(UUID companyId, String designCode);

    @Query("select d from JewelleryDesign d where d.id = :id and (:companyId is null or d.companyId = :companyId)")
    Optional<JewelleryDesign> findByIdInCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Query("""
            select d from JewelleryDesign d
            where (:companyId is null or d.companyId = :companyId)
              and (cast(:search as string) is null
                   or lower(d.name) like lower(concat('%', cast(:search as string), '%'))
                   or lower(d.designCode) like lower(concat('%', cast(:search as string), '%')))
              and (:collectionId is null or d.collection.id = :collectionId)
              and (:productTypeId is null or d.productType.id = :productTypeId)
            """)
    Page<JewelleryDesign> search(@Param("companyId") UUID companyId,
                                 @Param("search") String search,
                                 @Param("collectionId") UUID collectionId,
                                 @Param("productTypeId") UUID productTypeId,
                                 Pageable pageable);
}
