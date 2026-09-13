package com.finotech.jewellery.modules.approval.domain.entity;

import com.finotech.jewellery.modules.approval.domain.enums.ApprovalDecision;
import com.finotech.jewellery.modules.approval.domain.enums.ApprovalType;
import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One decision taken through the unified approvals endpoint. Stored so a
 * replayed request with the same idempotency key returns the original outcome
 * rather than approving (or rejecting) a second time.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "approval_decision", schema = "public")
public class ApprovalDecisionRecord extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_type", nullable = false, length = 30)
    private ApprovalType approvalType;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20)
    private ApprovalDecision decision;

    /** Status of the underlying record after the decision was applied. */
    @Column(name = "result_status", nullable = false, length = 30)
    private String resultStatus;

    @Column(name = "decided_by", nullable = false, length = 100)
    private String decidedBy;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;
}
