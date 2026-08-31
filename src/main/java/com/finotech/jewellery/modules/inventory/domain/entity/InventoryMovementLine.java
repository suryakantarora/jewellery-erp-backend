package com.finotech.jewellery.modules.inventory.domain.entity;

import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One item on a movement. The weight is captured at dispatch and again at
 * receipt so discrepancies are visible.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "inventory_movement_line", schema = "inventory")
public class InventoryMovementLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movement_id", nullable = false)
    private InventoryMovement movement;

    @Column(name = "jewellery_item_id", nullable = false)
    private UUID jewelleryItemId;

    @Column(name = "item_code", nullable = false, length = 50)
    private String itemCode;

    @Column(name = "dispatched_weight", precision = 12, scale = 3)
    private BigDecimal dispatchedWeight;

    @Column(name = "received_weight", precision = 12, scale = 3)
    private BigDecimal receivedWeight;

    @Column(name = "received", nullable = false)
    private boolean received;

    @Column(name = "discrepancy_note", length = 500)
    private String discrepancyNote;
}
