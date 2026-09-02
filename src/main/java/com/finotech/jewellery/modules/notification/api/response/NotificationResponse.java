package com.finotech.jewellery.modules.notification.api.response;

import com.finotech.jewellery.modules.notification.domain.entity.Notification;
import com.finotech.jewellery.modules.notification.domain.entity.NotificationTemplate;
import com.finotech.jewellery.modules.notification.domain.enums.NotificationChannel;
import com.finotech.jewellery.modules.notification.domain.enums.NotificationStatus;
import com.finotech.jewellery.modules.notification.domain.enums.RecipientType;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, String eventType, NotificationChannel channel,
                                   RecipientType recipientType, UUID recipientId,
                                   String recipientAddress, String subject, String body,
                                   NotificationStatus status, UUID branchId, String referenceType,
                                   String referenceId, int attemptCount, Instant sentAt,
                                   String failureReason, Instant readAt, Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getEventType(), n.getChannel(),
                n.getRecipientType(), n.getRecipientId(), n.getRecipientAddress(), n.getSubject(),
                n.getBody(), n.getStatus(), n.getBranchId(), n.getReferenceType(),
                n.getReferenceId(), n.getAttemptCount(), n.getSentAt(), n.getFailureReason(),
                n.getReadAt(), n.getCreatedAt());
    }

    public record TemplateResponse(UUID id, String code, String eventType,
                                   NotificationChannel channel, String locale, String subject,
                                   String body, boolean active) {

        public static TemplateResponse from(NotificationTemplate t) {
            return new TemplateResponse(t.getId(), t.getCode(), t.getEventType(), t.getChannel(),
                    t.getLocale(), t.getSubject(), t.getBody(), t.isActive());
        }
    }
}
