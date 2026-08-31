package com.finotech.jewellery.modules.inventory.infrastructure.repository;

import com.finotech.jewellery.modules.inventory.domain.entity.ItemLifecycleEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemLifecycleEventRepository extends JpaRepository<ItemLifecycleEvent, UUID> {

    List<ItemLifecycleEvent> findAllByJewelleryItemIdOrderByOccurredAtAsc(UUID jewelleryItemId);
}
