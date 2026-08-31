package com.finotech.jewellery.modules.product.infrastructure.repository;

import com.finotech.jewellery.modules.product.domain.entity.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SizeRepository extends JpaRepository<Size, UUID> {

    List<Size> findAllByProductTypeIdOrderByDisplayOrderAscCodeAsc(UUID productTypeId);

    boolean existsByProductTypeIdAndCodeIgnoreCase(UUID productTypeId, String code);
}
