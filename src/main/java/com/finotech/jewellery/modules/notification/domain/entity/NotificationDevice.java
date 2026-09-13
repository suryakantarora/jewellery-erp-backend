package com.finotech.jewellery.modules.notification.domain.entity;

import com.finotech.jewellery.modules.notification.domain.enums.DevicePlatform;
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
 * A phone (or browser) that can receive push messages for one user.
 *
 * <p>The token is the primary identity, not the user: a handset that changes
 * hands re-registers under the new owner, and the old owner must stop getting
 * its mail. Registration therefore re-owns an existing token rather than
 * refusing it.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "notification_device", schema = "public")
public class NotificationDevice extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token", nullable = false, unique = true, length = 512)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private DevicePlatform platform;

    @Column(name = "app_version", length = 50)
    private String appVersion;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
}
