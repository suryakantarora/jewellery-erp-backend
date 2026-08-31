package com.finotech.jewellery.modules.notification.domain.enums;

/**
 * Delivery state. Notifications are queued rather than sent inline so a slow or
 * failing provider never delays the business transaction that triggered them.
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED,
    /** Retries exhausted; needs manual attention. */
    ABANDONED,
    CANCELLED
}
