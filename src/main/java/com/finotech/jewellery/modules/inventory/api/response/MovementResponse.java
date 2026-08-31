package com.finotech.jewellery.modules.inventory.api.response;

import com.finotech.jewellery.modules.inventory.domain.entity.InventoryMovement;
import com.finotech.jewellery.modules.inventory.domain.enums.MovementStatus;
import com.finotech.jewellery.modules.inventory.domain.enums.MovementType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MovementResponse(UUID id, String referenceNumber, MovementType movementType,
                               MovementStatus status, UUID fromLocationId, UUID toLocationId,
                               UUID fromBranchId, UUID toBranchId, boolean requiresApproval,
                               String approvedBy, Instant approvedAt, String secondApprovedBy,
                               Instant secondApprovedAt, String dispatchedBy, Instant dispatchedAt,
                               String receivedBy, Instant completedAt, String rejectionReason,
                               String notes, List<MovementLineResponse> lines, Instant createdAt,
                               String createdBy) {

    public record MovementLineResponse(UUID id, UUID jewelleryItemId, String itemCode,
                                       BigDecimal dispatchedWeight, BigDecimal receivedWeight,
                                       boolean received, String discrepancyNote) {
    }

    public static MovementResponse from(InventoryMovement m) {
        List<MovementLineResponse> lines = m.getLines().stream()
                .map(l -> new MovementLineResponse(l.getId(), l.getJewelleryItemId(), l.getItemCode(),
                        l.getDispatchedWeight(), l.getReceivedWeight(), l.isReceived(),
                        l.getDiscrepancyNote()))
                .toList();
        return new MovementResponse(m.getId(), m.getReferenceNumber(), m.getMovementType(),
                m.getStatus(), m.getFromLocationId(), m.getToLocationId(), m.getFromBranchId(),
                m.getToBranchId(), m.isRequiresApproval(), m.getApprovedBy(), m.getApprovedAt(),
                m.getSecondApprovedBy(), m.getSecondApprovedAt(), m.getDispatchedBy(),
                m.getDispatchedAt(), m.getReceivedBy(), m.getCompletedAt(), m.getRejectionReason(),
                m.getNotes(), lines, m.getCreatedAt(), m.getCreatedBy());
    }
}
