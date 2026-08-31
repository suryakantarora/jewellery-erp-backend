package com.finotech.jewellery.modules.repair.api.response;

import com.finotech.jewellery.modules.repair.domain.entity.RepairRequest;
import com.finotech.jewellery.modules.repair.domain.enums.RepairStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record RepairResponse(UUID id, String requestNumber, UUID customerId, UUID branchId,
                             UUID jewelleryItemId, String itemDescription, RepairStatus status,
                             Set<RepairStatus> allowedTransitions, LocalDate receivedDate,
                             LocalDate promisedDate, String reportedProblem,
                             String conditionOnArrival, BigDecimal receivedWeight,
                             String conditionPhotoKeys, BigDecimal estimatedCost,
                             Integer estimatedDays, String estimateNotes, boolean customerApproved,
                             Instant customerResponseAt, String declineReason, String assignedTo,
                             BigDecimal finalCost, String currency, BigDecimal deliveredWeight,
                             Instant readyAt, Instant deliveredAt, String deliveredTo,
                             String notes, List<HistoryEntry> history) {

    public record HistoryEntry(RepairStatus fromStatus, RepairStatus toStatus, String performedBy,
                               String notes, Instant occurredAt) {
    }

    public static RepairResponse from(RepairRequest r) {
        return from(r, false);
    }

    public static RepairResponse withHistory(RepairRequest r) {
        return from(r, true);
    }

    private static RepairResponse from(RepairRequest r, boolean includeHistory) {
        List<HistoryEntry> entries = includeHistory
                ? r.getHistory().stream()
                        .map(h -> new HistoryEntry(h.getFromStatus(), h.getToStatus(),
                                h.getPerformedBy(), h.getNotes(), h.getOccurredAt()))
                        .toList()
                : null;
        return new RepairResponse(r.getId(), r.getRequestNumber(), r.getCustomerId(),
                r.getBranchId(), r.getJewelleryItemId(), r.getItemDescription(), r.getStatus(),
                r.getStatus().allowedTransitions(), r.getReceivedDate(), r.getPromisedDate(),
                r.getReportedProblem(), r.getConditionOnArrival(), r.getReceivedWeight(),
                r.getConditionPhotoKeys(), r.getEstimatedCost(), r.getEstimatedDays(),
                r.getEstimateNotes(), r.isCustomerApproved(), r.getCustomerResponseAt(),
                r.getDeclineReason(), r.getAssignedTo(), r.getFinalCost(), r.getCurrency(),
                r.getDeliveredWeight(), r.getReadyAt(), r.getDeliveredAt(), r.getDeliveredTo(),
                r.getNotes(), entries);
    }
}
