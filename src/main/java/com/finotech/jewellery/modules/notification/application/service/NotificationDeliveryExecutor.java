package com.finotech.jewellery.modules.notification.application.service;

import com.finotech.jewellery.modules.notification.application.sender.NotificationSender;
import com.finotech.jewellery.modules.notification.domain.entity.Notification;
import com.finotech.jewellery.modules.notification.domain.enums.NotificationChannel;
import com.finotech.jewellery.modules.notification.infrastructure.repository.NotificationRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivers a single notification in its own transaction.
 *
 * <p>This lives in a separate bean on purpose. Calling a {@code @Transactional}
 * method from another method of the same bean bypasses the Spring proxy, so the
 * status update would never commit and every message would be re-sent on the
 * next cycle. Going through a distinct bean guarantees the transaction — and so
 * guarantees a message is attempted once.
 */
@Slf4j
@Service
public class NotificationDeliveryExecutor {

    private final NotificationRepository notificationRepository;
    private final Map<NotificationChannel, NotificationSender> senders =
            new EnumMap<>(NotificationChannel.class);

    @Value("${jewellery.notification.max-attempts:3}")
    private int maxAttempts;

    public NotificationDeliveryExecutor(NotificationRepository notificationRepository,
                                        List<NotificationSender> availableSenders) {
        this.notificationRepository = notificationRepository;
        availableSenders.forEach(sender -> senders.put(sender.channel(), sender));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null || !notification.isRetryable(maxAttempts)) {
            return;
        }

        NotificationSender sender = senders.get(notification.getChannel());
        if (sender == null) {
            notification.markFailed(
                    "No sender configured for channel " + notification.getChannel(), maxAttempts);
            return;
        }

        try {
            sender.send(notification);
            notification.markSent();
        } catch (RuntimeException ex) {
            log.warn("Failed to deliver notification {}: {}", notificationId, ex.getMessage());
            notification.markFailed(ex.getMessage(), maxAttempts);
        }
    }
}
