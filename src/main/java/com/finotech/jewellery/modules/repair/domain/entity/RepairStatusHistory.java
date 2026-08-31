package com.finotech.jewellery.modules.repair.domain.entity;

import com.finotech.jewellery.modules.repair.domain.enums.RepairStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Append-only trail of every step a repair passed through, so a customer asking
 * "where is my ring" gets a real answer.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "repair_status_history", schema = "sales")
public class RepairStatusHistory {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repair_request_id", nullable = false)
    private RepairRequest repairRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private RepairStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private RepairStatus toStatus;

    @Column(name = "performed_by", length = 100)
    private String performedBy;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
