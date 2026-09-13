package com.finotech.jewellery.modules.notification.application.sender;

import com.finotech.jewellery.modules.notification.domain.entity.Notification;
import com.finotech.jewellery.modules.notification.domain.enums.NotificationChannel;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Development transports that log instead of sending.
 *
 * <p>They are deliberately the default: no real provider is configured yet, and
 * silently doing nothing would hide broken templates. Registering a real
 * provider bean for a channel replaces the logging one for that channel.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "jewellery.notification.transport", havingValue = "logging",
        matchIfMissing = true)
public class LoggingNotificationSender {

    @Bean
    public List<NotificationSender> loggingSenders() {
        return List.of();
    }

    @Bean
    public NotificationSender emailLoggingSender() {
        return channelSender(NotificationChannel.EMAIL);
    }

    @Bean
    public NotificationSender smsLoggingSender() {
        return channelSender(NotificationChannel.SMS);
    }

    /**
     * Stands in for FCM only while push is switched off; with
     * {@code jewellery.notification.push.enabled=true} the real sender owns the
     * channel and this bean must not compete for it.
     */
    @Bean
    @ConditionalOnProperty(prefix = "jewellery.notification.push", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    public NotificationSender pushLoggingSender() {
        // Push is addressed by user id (devices are looked up at send time), not
        // by an address string, so it must not fail on the missing address.
        return new NotificationSender() {
            @Override
            public NotificationChannel channel() {
                return NotificationChannel.PUSH;
            }

            @Override
            public void send(Notification notification) {
                log.info("[PUSH] to {} {} | {} | {}", notification.getRecipientType(),
                        notification.getRecipientId(), notification.getSubject(),
                        notification.getBody());
            }
        };
    }

    /** In-app messages need no transport: they are read from the database. */
    @Bean
    public NotificationSender inAppSender() {
        return new NotificationSender() {
            @Override
            public NotificationChannel channel() {
                return NotificationChannel.IN_APP;
            }

            @Override
            public void send(Notification notification) {
                // Delivered by being persisted; nothing further to do.
            }
        };
    }

    private NotificationSender channelSender(NotificationChannel channel) {
        return new NotificationSender() {
            @Override
            public NotificationChannel channel() {
                return channel;
            }

            @Override
            public void send(Notification notification) {
                if (!StringUtils.hasText(notification.getRecipientAddress())) {
                    throw new NotificationDeliveryException(
                            "No " + channel + " address for this recipient");
                }
                log.info("[{}] to {} | {} | {}", channel, notification.getRecipientAddress(),
                        notification.getSubject(), notification.getBody());
            }
        };
    }
}
