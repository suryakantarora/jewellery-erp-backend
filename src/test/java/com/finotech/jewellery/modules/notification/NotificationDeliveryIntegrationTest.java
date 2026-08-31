package com.finotech.jewellery.modules.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.finotech.jewellery.IntegrationTestBase;
import com.finotech.jewellery.TestSecurity;
import com.finotech.jewellery.modules.notification.application.service.NotificationDeliveryExecutor;
import com.finotech.jewellery.modules.notification.application.service.NotificationDispatcher;
import com.finotech.jewellery.modules.notification.domain.entity.Notification;
import com.finotech.jewellery.modules.notification.domain.enums.NotificationChannel;
import com.finotech.jewellery.modules.notification.domain.enums.NotificationStatus;
import com.finotech.jewellery.modules.notification.domain.enums.RecipientType;
import com.finotech.jewellery.modules.notification.infrastructure.repository.NotificationRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Delivery must be recorded, not just attempted.
 *
 * <p>These cover a real defect: the dispatcher originally called its own
 * {@code @Transactional} method, so Spring's proxy was bypassed, the status was
 * never persisted and every message was re-sent on each cycle. A customer would
 * have received the same SMS every thirty seconds.
 */
class NotificationDeliveryIntegrationTest extends IntegrationTestBase {

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationDispatcher dispatcher;
    @Autowired private NotificationDeliveryExecutor executor;

    @BeforeEach
    void authenticate() {
        TestSecurity.authenticateAsSuperAdmin();
    }

    @AfterEach
    void clearAuthentication() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("a delivered message is marked SENT and is not sent again")
    void deliveredMessageIsRecordedAndNotRepeated() {
        UUID id = queuePending("+8562055500001");

        dispatcher.dispatchPending();

        Notification afterFirst = notificationRepository.findById(id).orElseThrow();
        assertThat(afterFirst.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(afterFirst.getAttemptCount()).isEqualTo(1);
        assertThat(afterFirst.getSentAt()).isNotNull();

        // The critical assertion: a second cycle must not touch it again.
        dispatcher.dispatchPending();

        Notification afterSecond = notificationRepository.findById(id).orElseThrow();
        assertThat(afterSecond.getAttemptCount())
                .as("a sent message must not be re-delivered")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a message with no address fails and is abandoned once retries run out")
    void undeliverableMessageIsAbandoned() {
        UUID id = queuePending(null);

        // maxAttempts defaults to 3, so three cycles exhaust the retries.
        executor.deliver(id);
        assertThat(notificationRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.FAILED);

        executor.deliver(id);
        executor.deliver(id);

        Notification abandoned = notificationRepository.findById(id).orElseThrow();
        assertThat(abandoned.getStatus()).isEqualTo(NotificationStatus.ABANDONED);
        assertThat(abandoned.getAttemptCount()).isEqualTo(3);
        assertThat(abandoned.getFailureReason()).isNotBlank();

        // An abandoned message stays put rather than retrying forever.
        executor.deliver(id);
        assertThat(notificationRepository.findById(id).orElseThrow().getAttemptCount())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("in-app messages need no address")
    void inAppNeedsNoAddress() {
        Notification notification = new Notification();
        notification.setEventType("LOW_STOCK");
        notification.setChannel(NotificationChannel.IN_APP);
        notification.setRecipientType(RecipientType.USER);
        notification.setBody("Stock is low");
        notification.setStatus(NotificationStatus.PENDING);
        UUID id = notificationRepository.saveAndFlush(notification).getId();

        executor.deliver(id);

        assertThat(notificationRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.SENT);
    }

    private UUID queuePending(String address) {
        Notification notification = new Notification();
        notification.setEventType("SALE_COMPLETED");
        notification.setChannel(NotificationChannel.SMS);
        notification.setRecipientType(RecipientType.CUSTOMER);
        notification.setRecipientId(UUID.randomUUID());
        notification.setRecipientAddress(address);
        notification.setBody("Your purchase is complete.");
        notification.setStatus(NotificationStatus.PENDING);
        return notificationRepository.saveAndFlush(notification).getId();
    }
}
