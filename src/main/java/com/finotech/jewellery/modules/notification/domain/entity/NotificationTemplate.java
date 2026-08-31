package com.finotech.jewellery.modules.notification.domain.entity;

import com.finotech.jewellery.modules.notification.domain.enums.NotificationChannel;
import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The message sent for one event on one channel.
 *
 * <p>Bodies use {@code {{placeholder}}} markers filled from the event payload,
 * so wording and language can change without a deployment.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "notification_template", schema = "public",
        uniqueConstraints = @UniqueConstraint(name = "uq_template_event_channel_locale",
                columnNames = {"event_type", "channel", "locale"}))
public class NotificationTemplate extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 60)
    private String code;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "locale", nullable = false, length = 10)
    private String locale = "en";

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * Substitutes {@code {{key}}} markers. An unknown marker is left in place
     * rather than blanked, so a broken template is obvious in the output
     * instead of silently producing a half-empty message.
     */
    public String render(String text, Map<String, Object> values) {
        if (text == null) {
            return null;
        }
        String rendered = text;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}",
                    String.valueOf(entry.getValue()));
        }
        return rendered;
    }
}
