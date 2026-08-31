package com.finotech.jewellery.modules.notification.application.sender;

import com.finotech.jewellery.modules.notification.domain.entity.Notification;
import com.finotech.jewellery.modules.notification.domain.enums.NotificationChannel;

/**
 * A transport that actually delivers a message.
 *
 * <p>One implementation per channel. Swapping in a real email or SMS provider
 * means adding an implementation, not touching the modules that raise events.
 */
public interface NotificationSender {

    NotificationChannel channel();

    /**
     * @throws NotificationDeliveryException when the message could not be sent;
     *         the queue then retries according to the configured limit
     */
    void send(Notification notification);

    class NotificationDeliveryException extends RuntimeException {
        public NotificationDeliveryException(String message) {
            super(message);
        }
    }
}
