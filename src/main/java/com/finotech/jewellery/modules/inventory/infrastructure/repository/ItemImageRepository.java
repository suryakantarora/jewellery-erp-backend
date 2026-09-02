package com.finotech.jewellery.modules.inventory.infrastructure.repository;

import com.finotech.jewellery.modules.inventory.domain.entity.ItemImage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemImageRepository extends JpaRepository<ItemImage, UUID> {

    List<ItemImage> findAllByItemIdOrderByDisplayOrderAscCreatedAtAsc(UUID itemId);

    List<ItemImage> findAllByItemIdAndPrimaryImageTrue(UUID itemId);
}
