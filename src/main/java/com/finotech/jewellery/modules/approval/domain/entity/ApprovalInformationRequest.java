package com.finotech.jewellery.modules.approval.domain.entity;

import com.finotech.jewellery.modules.approval.domain.enums.ApprovalType;
import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A question an approver put to the person who raised a record. It lives
 * beside the record and never alters its status: the approver is asking, not
 * deciding.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "approval_information_request", schema = "public")
public class ApprovalInformationRequest extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_type", nullable = false, length = 30)
    private ApprovalType approvalType;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "answer", length = 500)
    private String answer;

    @Column(name = "answered_by", length = 100)
    private String answeredBy;

    public boolean isOpen() {
        return answeredAt == null;
    }
}
