package com.finotech.jewellery.modules.product.infrastructure.repository;

import com.finotech.jewellery.modules.product.domain.entity.ProductCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);

    @Query("""
            select c from ProductCategory c
            where (:companyId is null or c.companyId = :companyId)
            order by c.displayOrder asc, c.name asc
            """)
    List<ProductCategory> findAllInCompany(@Param("companyId") UUID companyId);

    @Query("select c from ProductCategory c where c.id = :id and (:companyId is null or c.companyId = :companyId)")
    Optional<ProductCategory> findByIdInCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    boolean existsByParentId(UUID parentId);
}
