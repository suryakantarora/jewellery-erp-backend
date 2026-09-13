package com.finotech.jewellery.modules.product.infrastructure.repository;

import com.finotech.jewellery.modules.product.domain.entity.ProductImage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findAllByProductIdOrderByDisplayOrderAscCreatedAtAsc(UUID productId);

    List<ProductImage> findAllByProductIdAndPrimaryImageTrue(UUID productId);
}
