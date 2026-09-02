package com.finotech.jewellery.modules.notification.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Records that one user has read one notification.
 *
 * <p>Read state cannot live on the notification itself. Operational events are
 * queued once per branch with a null recipient, so a single row is the inbox
 * entry for every member of that branch; a column there would make one person's
 * "read" everybody's.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "notification_read", schema = "public")
public class NotificationRead {

    @EmbeddedId
    private Id id;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    public NotificationRead(UUID notificationId, UUID userId, Instant readAt) {
        this.id = new Id(notificationId, userId);
        this.readAt = readAt;
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Id implements Serializable {

        @Column(name = "notification_id", nullable = false)
        private UUID notificationId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        public Id(UUID notificationId, UUID userId) {
            this.notificationId = notificationId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Id that)) {
                return false;
            }
            return Objects.equals(notificationId, that.notificationId)
                    && Objects.equals(userId, that.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(notificationId, userId);
        }
    }
}
