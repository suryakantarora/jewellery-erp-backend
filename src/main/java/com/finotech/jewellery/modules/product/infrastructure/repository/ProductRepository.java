package com.finotech.jewellery.modules.product.infrastructure.repository;

import com.finotech.jewellery.modules.product.domain.entity.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsByCompanyIdAndSkuIgnoreCase(UUID companyId, String sku);

    @Query("select p from Product p where p.id = :id and (:companyId is null or p.companyId = :companyId)")
    Optional<Product> findByIdInCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @EntityGraph(attributePaths = "images")
    Optional<Product> findWithImagesById(UUID id);

    @Query("""
            select p from Product p
            where (:companyId is null or p.companyId = :companyId)
              and (cast(:search as string) is null
                   or lower(p.name) like lower(concat('%', cast(:search as string), '%'))
                   or lower(p.sku) like lower(concat('%', cast(:search as string), '%')))
              and (:categoryId is null or p.category.id = :categoryId)
              and (:productTypeId is null or p.productType.id = :productTypeId)
              and (:brandId is null or p.brand.id = :brandId)
              and (:collectionId is null or p.collection.id = :collectionId)
            """)
    Page<Product> search(@Param("companyId") UUID companyId,
                         @Param("search") String search,
                         @Param("categoryId") UUID categoryId,
                         @Param("productTypeId") UUID productTypeId,
                         @Param("brandId") UUID brandId,
                         @Param("collectionId") UUID collectionId,
                         Pageable pageable);
}
