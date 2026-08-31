package com.finotech.jewellery.modules.notification.application.service;

import com.finotech.jewellery.modules.customer.application.CustomerDirectory;
import com.finotech.jewellery.modules.notification.domain.entity.Notification;
import com.finotech.jewellery.modules.notification.domain.entity.NotificationTemplate;
import com.finotech.jewellery.modules.notification.domain.enums.NotificationChannel;
import com.finotech.jewellery.modules.notification.domain.enums.NotificationStatus;
import com.finotech.jewellery.modules.notification.domain.enums.RecipientType;
import com.finotech.jewellery.modules.notification.infrastructure.repository.NotificationRepository;
import com.finotech.jewellery.modules.notification.infrastructure.repository.NotificationTemplateRepository;
import com.finotech.jewellery.shared.event.DomainEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns business events into queued messages.
 *
 * <p>Queuing runs in its own transaction: a notification problem must never
 * roll back the sale or repair that triggered it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationTemplateRepository templateRepository;
    private final NotificationRepository notificationRepository;
    private final CustomerDirectory customerDirectory;

    @Value("${jewellery.notification.max-attempts:3}")
    private int maxAttempts;

    /**
     * Queues one message per active template registered for the event.
     *
     * <p>An event with no template is not an error: it simply has nothing to
     * announce yet, which is what lets templates be added later without code.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void queueFor(DomainEvent event, RecipientType recipientType, UUID recipientId,
                         String referenceType, Object referenceId) {
        List<NotificationTemplate> templates =
                templateRepository.findAllByEventTypeAndActiveTrue(event.eventType());
        if (templates.isEmpty()) {
            log.debug("No notification template registered for {}", event.eventType());
            return;
        }

        Map<String, Object> values = new HashMap<>(event.payload());
        String address = resolveAddress(recipientType, recipientId, values);

        for (NotificationTemplate template : templates) {
            Notification notification = new Notification();
            notification.setEventType(event.eventType());
            notification.setChannel(template.getChannel());
            notification.setRecipientType(recipientType);
            notification.setRecipientId(recipientId);
            notification.setRecipientAddress(address);
            notification.setSubject(template.render(template.getSubject(), values));
            notification.setBody(template.render(template.getBody(), values));
            notification.setBranchId(event.branchId());
            notification.setReferenceType(referenceType);
            notification.setReferenceId(referenceId == null ? null : String.valueOf(referenceId));
            notification.setStatus(NotificationStatus.PENDING);
            notificationRepository.save(notification);
        }
    }

    @Transactional(readOnly = true)
    public List<Notification> dispatchable(int batchSize) {
        return notificationRepository.findDispatchable(maxAttempts, Pageable.ofSize(batchSize));
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * Fills in recipient details a template may reference, and returns the
     * address to deliver to.
     */
    private String resolveAddress(RecipientType recipientType, UUID recipientId,
                                  Map<String, Object> values) {
        if (recipientType != RecipientType.CUSTOMER || recipientId == null) {
            return null;
        }
        try {
            CustomerDirectory.CustomerView customer = customerDirectory.requireCustomer(recipientId);
            values.putIfAbsent("customerName", customer.fullName());
            values.putIfAbsent("customerCode", customer.customerCode());
            return customer.phone();
        } catch (RuntimeException ex) {
            log.warn("Could not resolve notification recipient {}: {}", recipientId, ex.getMessage());
            return null;
        }
    }
}
