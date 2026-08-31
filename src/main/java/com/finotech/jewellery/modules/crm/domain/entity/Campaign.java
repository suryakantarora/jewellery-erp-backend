package com.finotech.jewellery.modules.crm.domain.entity;

import com.finotech.jewellery.modules.crm.domain.enums.CampaignStatus;
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
 * An outreach campaign to a segment of customers.
 *
 * <p>The campaign names a notification template rather than carrying its own
 * message body, so wording, channel and language stay in one place.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "campaign", schema = "crm")
public class Campaign extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Column(name = "segment_id")
    private UUID segmentId;

    /** Notification template used for the message. */
    @Column(name = "template_code", length = 60)
    private String templateCode;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "starts_on")
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(name = "target_count", nullable = false)
    private int targetCount;

    @Column(name = "sent_count", nullable = false)
    private int sentCount;

    @Column(name = "launched_at")
    private Instant launchedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
