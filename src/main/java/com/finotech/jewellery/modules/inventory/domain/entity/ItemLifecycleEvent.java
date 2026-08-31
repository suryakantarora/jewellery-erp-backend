package com.finotech.jewellery.modules.inventory.domain.entity;

import com.finotech.jewellery.modules.inventory.domain.enums.LifecycleEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One append-only entry in an item's digital passport. Together with the
 * current state on {@link JewelleryItem}, this gives both fast operational
 * queries and full traceability (section 17).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "item_lifecycle_event", schema = "inventory")
public class ItemLifecycleEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "jewellery_item_id", nullable = false)
    private UUID jewelleryItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private LifecycleEventType eventType;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", length = 20)
    private String toStatus;

    @Column(name = "from_location_id")
    private UUID fromLocationId;

    @Column(name = "to_location_id")
    private UUID toLocationId;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "performed_by", length = 100)
    private String performedBy;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
