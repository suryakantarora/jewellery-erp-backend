package com.finotech.jewellery.shared.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * The single way modules raise business events.
 *
 * <p>Wrapping Spring's publisher keeps the modules free of framework detail and
 * gives one place to add a message broker later: listeners already run after
 * commit, which is the same delivery guarantee an outbox would provide.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    private final ApplicationEventPublisher publisher;

    public void publish(DomainEvent event) {
        log.debug("Publishing {} for branch {}", event.eventType(), event.branchId());
        publisher.publishEvent(event);
    }
}
