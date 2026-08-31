package com.finotech.jewellery.shared.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(UUID id,
                               UUID userId,
                               String username,
                               String action,
                               String entityType,
                               String entityId,
                               String oldValue,
                               String newValue,
                               UUID branchId,
                               String correlationId,
                               Instant occurredAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getUserId(), log.getUsername(), log.getAction(),
                log.getEntityType(), log.getEntityId(), log.getOldValue(), log.getNewValue(),
                log.getBranchId(), log.getCorrelationId(), log.getOccurredAt());
    }
}
