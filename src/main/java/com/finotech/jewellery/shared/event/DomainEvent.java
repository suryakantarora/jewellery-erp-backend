package com.finotech.jewellery.shared.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A business fact that has happened. Modules publish these instead of calling
 * each other's side effects directly (section 13).
 *
 * <p>Events are published in-process today and delivered after the publishing
 * transaction commits. The same events can later be forwarded to Kafka or
 * RabbitMQ without changing the modules that raise them.
 */
public interface DomainEvent {

    /** Stable name used for routing and for notification templates. */
    String eventType();

    Instant occurredAt();

    /** Branch the event happened at, where it is branch-scoped. */
    UUID branchId();

    /**
     * Values available to a notification template, e.g. {@code invoiceNumber}.
     */
    Map<String, Object> payload();
}
