package com.finotech.jewellery.modules.inventory.api.response;

import com.finotech.jewellery.modules.inventory.domain.entity.ItemLifecycleEvent;
import com.finotech.jewellery.modules.inventory.domain.enums.LifecycleEventType;
import java.time.Instant;
import java.util.UUID;

public record LifecycleEventResponse(UUID id, LifecycleEventType eventType, String fromStatus,
                                     String toStatus, UUID fromLocationId, UUID toLocationId,
                                     String referenceType, String referenceId, String performedBy,
                                     String notes, Instant occurredAt) {

    public static LifecycleEventResponse from(ItemLifecycleEvent e) {
        return new LifecycleEventResponse(e.getId(), e.getEventType(), e.getFromStatus(),
                e.getToStatus(), e.getFromLocationId(), e.getToLocationId(), e.getReferenceType(),
                e.getReferenceId(), e.getPerformedBy(), e.getNotes(), e.getOccurredAt());
    }
}
