package com.finotech.jewellery.modules.inventory.domain.entity;

import com.finotech.jewellery.modules.inventory.domain.enums.MovementStatus;
import com.finotech.jewellery.modules.inventory.domain.enums.MovementType;
import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A document moving one or more items between locations, with its own approval
 * workflow. The header carries the route and status; each
 * {@link InventoryMovementLine} names one item.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "inventory_movement", schema = "inventory")
public class InventoryMovement extends BaseEntity {

    @Column(name = "reference_number", nullable = false, unique = true, length = 50)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private MovementType movementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MovementStatus status = MovementStatus.DRAFT;

    @Column(name = "from_location_id")
    private UUID fromLocationId;

    @Column(name = "to_location_id")
    private UUID toLocationId;

    @Column(name = "from_branch_id")
    private UUID fromBranchId;

    @Column(name = "to_branch_id")
    private UUID toBranchId;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    /** Second approver, required when either endpoint is a dual-authorization vault. */
    @Column(name = "second_approved_by", length = 100)
    private String secondApprovedBy;

    @Column(name = "second_approved_at")
    private Instant secondApprovedAt;

    @Column(name = "dispatched_by", length = 100)
    private String dispatchedBy;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "received_by", length = 100)
    private String receivedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "notes", length = 500)
    private String notes;

    @OneToMany(mappedBy = "movement", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<InventoryMovementLine> lines = new ArrayList<>();

    public void addLine(InventoryMovementLine line) {
        line.setMovement(this);
        lines.add(line);
    }

    public boolean isEditable() {
        return status == MovementStatus.DRAFT || status == MovementStatus.PENDING_APPROVAL;
    }
}
