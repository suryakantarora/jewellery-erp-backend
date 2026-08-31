package com.finotech.jewellery.shared.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Immutable audit trail for sensitive operations (section 23). Written in its
 * own transaction so a rolled-back business action still leaves a trace.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "audit_log", schema = "audit")
public class AuditLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", length = 100)
    private String entityId;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
