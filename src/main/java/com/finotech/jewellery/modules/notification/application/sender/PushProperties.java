package com.finotech.jewellery.modules.notification.application.sender;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Firebase Cloud Messaging settings.
 *
 * @param enabled         when false (the default) PUSH rows go to the logging
 *                        transport, so a deployment without a Firebase project
 *                        still runs
 * @param credentialsFile path to the service-account JSON downloaded from the
 *                        Firebase console; {@code FCM_CREDENTIALS_FILE} in the
 *                        environment
 */
@ConfigurationProperties(prefix = "jewellery.notification.push")
public record PushProperties(boolean enabled, String credentialsFile) {
}
