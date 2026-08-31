package com.finotech.jewellery.modules.product.infrastructure.repository;

import com.finotech.jewellery.modules.product.domain.entity.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionRepository extends JpaRepository<Collection, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    List<Collection> findAllByOrderByNameAsc();
}
