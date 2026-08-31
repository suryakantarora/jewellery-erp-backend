package com.finotech.jewellery.modules.inventory.application.service;

import com.finotech.jewellery.modules.inventory.domain.entity.ItemLifecycleEvent;
import com.finotech.jewellery.modules.inventory.domain.enums.ItemStatus;
import com.finotech.jewellery.modules.inventory.domain.enums.LifecycleEventType;
import com.finotech.jewellery.modules.inventory.infrastructure.repository.ItemLifecycleEventRepository;
import com.finotech.jewellery.shared.security.SecurityUtils;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends passport entries. Kept separate from the item service so every path
 * that changes an item records history the same way.
 */
@Service
@RequiredArgsConstructor
public class ItemLifecycleRecorder {

    private final ItemLifecycleEventRepository repository;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void record(UUID itemId, LifecycleEventType type, ItemStatus from, ItemStatus to,
                       UUID fromLocationId, UUID toLocationId,
                       String referenceType, Object referenceId, String notes) {
        ItemLifecycleEvent event = new ItemLifecycleEvent();
        event.setJewelleryItemId(itemId);
        event.setEventType(type);
        event.setFromStatus(from == null ? null : from.name());
        event.setToStatus(to == null ? null : to.name());
        event.setFromLocationId(fromLocationId);
        event.setToLocationId(toLocationId);
        event.setReferenceType(referenceType);
        event.setReferenceId(referenceId == null ? null : String.valueOf(referenceId));
        event.setPerformedBy(SecurityUtils.currentUsername().orElse("system"));
        event.setNotes(notes);
        event.setOccurredAt(Instant.now());
        repository.save(event);
    }

    public void record(UUID itemId, LifecycleEventType type, String notes) {
        record(itemId, type, null, null, null, null, null, null, notes);
    }
}
