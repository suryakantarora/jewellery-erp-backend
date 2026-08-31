package com.finotech.jewellery.modules.notification.application.service;

import com.finotech.jewellery.modules.notification.domain.entity.Notification;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Drains the notification queue on a schedule.
 *
 * <p>Each message is delivered through {@link NotificationDeliveryExecutor} in
 * its own transaction, so one bad address cannot block the rest of the batch and
 * a delivered message is always recorded as such.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationService notificationService;
    private final NotificationDeliveryExecutor deliveryExecutor;

    @Value("${jewellery.notification.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${jewellery.notification.dispatch-interval-ms:30000}")
    public void dispatchPending() {
        List<Notification> batch = notificationService.dispatchable(batchSize);
        if (batch.isEmpty()) {
            return;
        }
        log.debug("Dispatching {} notification(s)", batch.size());
        batch.forEach(notification -> deliveryExecutor.deliver(notification.getId()));
    }
}
