package com.finotech.jewellery.modules.product.infrastructure.repository;

import com.finotech.jewellery.modules.product.domain.entity.ProductType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductTypeRepository extends JpaRepository<ProductType, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    List<ProductType> findAllByOrderByNameAsc();
}
