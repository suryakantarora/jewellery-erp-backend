package com.finotech.jewellery.modules.notification.application.sender;

import com.finotech.jewellery.modules.notification.domain.entity.Notification;
import com.finotech.jewellery.modules.notification.domain.entity.NotificationDevice;
import com.finotech.jewellery.modules.notification.domain.enums.NotificationChannel;
import com.finotech.jewellery.modules.notification.domain.enums.RecipientType;
import com.finotech.jewellery.modules.notification.infrastructure.repository.NotificationDeviceRepository;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Delivers PUSH rows through Firebase Cloud Messaging.
 *
 * <p>Active only when {@code jewellery.notification.push.enabled=true}; the
 * logging transport covers PUSH otherwise, so the two can never both claim the
 * channel. Firebase is initialised lazily from the service-account file: a
 * missing file is a configuration problem reported once at startup and again
 * on each row (as a FAILED attempt), never an exception loop.
 *
 * <p>Data keys sent with every message, so the app can deep-link:
 * {@code eventType}, {@code referenceType}, {@code referenceId},
 * {@code notificationId}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "jewellery.notification.push", name = "enabled",
        havingValue = "true")
public class FcmNotificationSender implements NotificationSender {

    static final String NO_DEVICE = "no registered device";
    static final String NOT_CONFIGURED = "push not configured";

    private final PushProperties properties;
    private final NotificationDeviceRepository deviceRepository;

    private volatile FirebaseMessaging messaging;

    @PostConstruct
    void warnIfUnconfigured() {
        if (!StringUtils.hasText(properties.credentialsFile())
                || !Files.isReadable(Path.of(properties.credentialsFile()))) {
            log.warn("Push is enabled but jewellery.notification.push.credentials-file "
                    + "({}) is missing or unreadable: every PUSH notification will fail "
                    + "with '{}' until it is provided", properties.credentialsFile(),
                    NOT_CONFIGURED);
        }
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.PUSH;
    }

    @Override
    public void send(Notification notification) {
        // Customers, broadcasts and fixed addresses have no handset to reach.
        if (notification.getRecipientType() != RecipientType.USER
                || notification.getRecipientId() == null) {
            throw new NotificationCancelledException(NO_DEVICE);
        }
        List<NotificationDevice> devices =
                deviceRepository.findAllByUserId(notification.getRecipientId());
        if (devices.isEmpty()) {
            throw new NotificationCancelledException(NO_DEVICE);
        }

        FirebaseMessaging client = messagingClient();
        if (client == null) {
            throw new NotificationDeliveryException(NOT_CONFIGURED);
        }

        List<String> tokens = devices.stream().map(NotificationDevice::getToken).toList();
        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(notification.getSubject() == null
                                ? notification.getEventType() : notification.getSubject())
                        .setBody(notification.getBody())
                        .build())
                .putAllData(dataOf(notification))
                .build();

        BatchResponse response;
        try {
            response = client.sendEachForMulticast(message);
        } catch (FirebaseMessagingException ex) {
            throw new NotificationDeliveryException("FCM send failed: " + ex.getMessage());
        }

        List<String> deadTokens = new ArrayList<>();
        String lastError = null;
        for (int i = 0; i < response.getResponses().size(); i++) {
            SendResponse result = response.getResponses().get(i);
            if (result.isSuccessful()) {
                continue;
            }
            FirebaseMessagingException ex = result.getException();
            lastError = ex == null ? "unknown error" : ex.getMessage();
            if (ex != null && isDeadToken(ex.getMessagingErrorCode())) {
                deadTokens.add(tokens.get(i));
            }
        }
        if (!deadTokens.isEmpty()) {
            // The phone uninstalled the app or the token was rotated: forget it,
            // otherwise every future message pays for the same failure.
            deviceRepository.deleteAllByTokenIn(deadTokens);
            log.info("Removed {} unregistered push token(s) for user {}", deadTokens.size(),
                    notification.getRecipientId());
        }
        if (response.getSuccessCount() == 0) {
            throw new NotificationDeliveryException("FCM rejected every device: " + lastError);
        }
    }

    private static boolean isDeadToken(MessagingErrorCode code) {
        return code == MessagingErrorCode.UNREGISTERED
                || code == MessagingErrorCode.INVALID_ARGUMENT;
    }

    /** FCM data values must be strings and must not be null. */
    private static Map<String, String> dataOf(Notification notification) {
        Map<String, String> data = new HashMap<>();
        data.put("eventType", nullSafe(notification.getEventType()));
        data.put("referenceType", nullSafe(notification.getReferenceType()));
        data.put("referenceId", nullSafe(notification.getReferenceId()));
        data.put("notificationId", String.valueOf(notification.getId()));
        return data;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /**
     * Initialises Firebase on first use. Returns null, rather than throwing,
     * when the credentials file is absent so the caller can fail the row with
     * a readable reason.
     */
    private FirebaseMessaging messagingClient() {
        FirebaseMessaging current = messaging;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (messaging != null) {
                return messaging;
            }
            String file = properties.credentialsFile();
            if (!StringUtils.hasText(file) || !Files.isReadable(Path.of(file))) {
                return null;
            }
            try (InputStream in = new FileInputStream(file)) {
                FirebaseApp app;
                if (FirebaseApp.getApps().isEmpty()) {
                    app = FirebaseApp.initializeApp(FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(in))
                            .build());
                } else {
                    app = FirebaseApp.getInstance();
                }
                messaging = FirebaseMessaging.getInstance(app);
                log.info("Firebase Cloud Messaging initialised from {}", file);
                return messaging;
            } catch (IOException | RuntimeException ex) {
                log.error("Could not initialise Firebase from {}: {}", file, ex.getMessage());
                return null;
            }
        }
    }
}
