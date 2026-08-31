package com.finotech.jewellery.modules.crm.domain.entity;

import com.finotech.jewellery.modules.crm.domain.enums.ActivityType;
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
 * A recorded interaction with a customer: a call, a showroom visit, a
 * complaint. This is CRM behaviour, deliberately separate from the customer
 * master record (section 17).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "customer_activity", schema = "crm")
public class CustomerActivity extends BaseEntity {

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 30)
    private ActivityType activityType;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "details", columnDefinition = "text")
    private String details;

    @Column(name = "branch_id")
    private UUID branchId;

    /** Staff member who had the interaction. */
    @Column(name = "handled_by", length = 100)
    private String handledBy;

    /** Links the activity to a sale, repair or campaign where relevant. */
    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
