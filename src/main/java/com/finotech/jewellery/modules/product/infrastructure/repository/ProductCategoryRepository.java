package com.finotech.jewellery.modules.product.infrastructure.repository;

import com.finotech.jewellery.modules.product.domain.entity.ProductCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    List<ProductCategory> findAllByOrderByDisplayOrderAscNameAsc();

    boolean existsByParentId(UUID parentId);
}
