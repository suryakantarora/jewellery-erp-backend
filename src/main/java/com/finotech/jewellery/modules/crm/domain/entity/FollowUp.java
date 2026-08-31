package com.finotech.jewellery.modules.crm.domain.entity;

import com.finotech.jewellery.modules.crm.domain.enums.FollowUpStatus;
import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A task to come back to a customer: chase a quotation, wish them on an
 * anniversary, check a repair landed well.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "follow_up", schema = "crm")
public class FollowUp extends BaseEntity {

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /** Username of the staff member responsible. */
    @Column(name = "assigned_to", nullable = false, length = 100)
    private String assignedTo;

    @Column(name = "branch_id")
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FollowUpStatus status = FollowUpStatus.OPEN;

    @Column(name = "priority", length = 20)
    private String priority;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by", length = 100)
    private String completedBy;

    @Column(name = "outcome", length = 1000)
    private String outcome;

    public boolean isOverdue() {
        return status == FollowUpStatus.OPEN && dueDate.isBefore(LocalDate.now());
    }
}
