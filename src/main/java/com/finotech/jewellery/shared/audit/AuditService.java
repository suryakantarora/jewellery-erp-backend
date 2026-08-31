package com.finotech.jewellery.shared.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finotech.jewellery.shared.config.CorrelationIdFilter;
import com.finotech.jewellery.shared.security.AuthenticatedUser;
import com.finotech.jewellery.shared.security.SecurityUtils;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single entry point for writing audit records. Modules call this for any
 * sensitive action rather than logging ad hoc.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, Object entityId,
                       Object oldValue, Object newValue) {
        record(action, entityType, entityId, oldValue, newValue, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, Object entityId,
                       Object oldValue, Object newValue, UUID branchId) {
        try {
            AuditLog entry = new AuditLog();
            SecurityUtils.currentUser().ifPresent(user -> {
                entry.setUserId(user.userId());
                entry.setUsername(user.username());
            });
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId == null ? null : String.valueOf(entityId));
            entry.setOldValue(toJson(oldValue));
            entry.setNewValue(toJson(newValue));
            entry.setBranchId(branchId);
            entry.setCorrelationId(CorrelationIdFilter.current());
            entry.setOccurredAt(Instant.now());
            repository.save(entry);
        } catch (RuntimeException ex) {
            // Auditing must never break the business operation it observes.
            log.error("Failed to write audit record action={} entityType={}", action, entityType, ex);
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }
}
