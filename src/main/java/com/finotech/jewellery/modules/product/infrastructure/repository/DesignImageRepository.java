package com.finotech.jewellery.modules.product.infrastructure.repository;

import com.finotech.jewellery.modules.product.domain.entity.DesignImage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DesignImageRepository extends JpaRepository<DesignImage, UUID> {

    List<DesignImage> findAllByDesignIdOrderByDisplayOrderAscCreatedAtAsc(UUID designId);

    List<DesignImage> findAllByDesignIdAndPrimaryImageTrue(UUID designId);
}
