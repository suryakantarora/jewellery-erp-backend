package com.finotech.jewellery.modules.storefront.application;

import com.finotech.jewellery.modules.notification.application.sender.PushProperties;
import com.finotech.jewellery.modules.storefront.infrastructure.CustomerAppStore;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Best-effort push to a customer's handsets. The inbox row is the record; a
 * push that cannot be sent is logged and dropped, never an error for the
 * caller. Uses the same Firebase project and switch as staff push
 * ({@code jewellery.notification.push.*}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerPushSender {

    private final PushProperties properties;
    private final CustomerAppStore store;

    private volatile FirebaseMessaging messaging;

    public void send(UUID customerId, String title, String body, String link) {
        if (!properties.enabled()) {
            return;
        }
        List<String> tokens = store.deviceTokens(customerId);
        if (tokens.isEmpty()) {
            return;
        }
        FirebaseMessaging client = client();
        if (client == null) {
            return;
        }
        try {
            BatchResponse response = client.sendEachForMulticast(MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .putData("link", link == null ? "" : link)
                    .build());
            for (int i = 0; i < response.getResponses().size(); i++) {
                FirebaseMessagingException ex = response.getResponses().get(i).getException();
                if (ex != null && (ex.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                        || ex.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT)) {
                    store.forgetDeviceToken(tokens.get(i));
                }
            }
        } catch (FirebaseMessagingException | RuntimeException ex) {
            log.warn("Customer push failed for {}: {}", customerId, ex.getMessage());
        }
    }

    private FirebaseMessaging client() {
        if (messaging != null) {
            return messaging;
        }
        synchronized (FirebaseApp.class) {
            if (messaging != null) {
                return messaging;
            }
            String file = properties.credentialsFile();
            if (!StringUtils.hasText(file) || !Files.isReadable(Path.of(file))) {
                return null;
            }
            try (InputStream in = new FileInputStream(file)) {
                FirebaseApp app = FirebaseApp.getApps().isEmpty()
                        ? FirebaseApp.initializeApp(FirebaseOptions.builder()
                                .setCredentials(GoogleCredentials.fromStream(in)).build())
                        : FirebaseApp.getInstance();
                messaging = FirebaseMessaging.getInstance(app);
                return messaging;
            } catch (IOException | RuntimeException ex) {
                log.error("Could not initialise Firebase for customer push: {}", ex.getMessage());
                return null;
            }
        }
    }
}
