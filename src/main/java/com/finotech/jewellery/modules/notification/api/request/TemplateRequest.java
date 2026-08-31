package com.finotech.jewellery.modules.notification.api.request;

import com.finotech.jewellery.modules.notification.domain.enums.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Bodies may contain {@code {{placeholder}}} markers filled from the event
 * payload, e.g. {@code {{invoiceNumber}}}.
 */
public record TemplateRequest(@NotBlank @Size(max = 60) String code,
                              @NotBlank @Size(max = 60) String eventType,
                              @NotNull NotificationChannel channel,
                              @Size(max = 10) String locale,
                              @Size(max = 255) String subject,
                              @NotBlank String body) {
}
