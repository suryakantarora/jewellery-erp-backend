package com.finotech.jewellery.modules.notification.domain.entity;

import com.finotech.jewellery.modules.notification.domain.enums.NotificationChannel;
import com.finotech.jewellery.modules.notification.domain.enums.NotificationStatus;
import com.finotech.jewellery.modules.notification.domain.enums.RecipientType;
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
 * One queued message. This is an outbox: rows are written inside the business
 * transaction's listener and dispatched separately, so delivery failures never
 * roll back a sale.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "notification", schema = "public")
public class Notification extends BaseEntity {

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 20)
    private RecipientType recipientType;

    @Column(name = "recipient_id")
    private UUID recipientId;

    /** Resolved address: an email, phone number or device token. */
    @Column(name = "recipient_address", length = 255)
    private String recipientAddress;

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "branch_id")
    private UUID branchId;

    /** Links the message back to the sale, repair or transfer that caused it. */
    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
        this.lastAttemptAt = Instant.now();
        this.attemptCount++;
        this.failureReason = null;
    }

    /**
     * Records a failed attempt, abandoning the message once retries are spent so
     * a permanently bad address does not retry forever.
     */
    public void markFailed(String reason, int maxAttempts) {
        this.attemptCount++;
        this.lastAttemptAt = Instant.now();
        this.failureReason = reason;
        this.status = attemptCount >= maxAttempts
                ? NotificationStatus.ABANDONED
                : NotificationStatus.FAILED;
    }

    public boolean isRetryable(int maxAttempts) {
        return (status == NotificationStatus.PENDING || status == NotificationStatus.FAILED)
                && attemptCount < maxAttempts;
    }
}
