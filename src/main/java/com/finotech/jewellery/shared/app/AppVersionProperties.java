package com.finotech.jewellery.shared.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-platform app version policy bound from {@code jewellery.app.versions.*}.
 * Missing entries fall back to {@code 0.1.0} with no store link, which makes
 * every client "supported" until an operator decides otherwise.
 */
@ConfigurationProperties(prefix = "jewellery.app")
public record AppVersionProperties(Versions versions) {

    public record Versions(Platform android, Platform ios) {
    }

    public record Platform(String minSupported, String latest, String storeUrl, String message) {

        public static final Platform DEFAULT = new Platform("0.1.0", "0.1.0", "", null);

        /** Fills unset fields with defaults so the response never carries nulls it shouldn't. */
        public Platform normalised() {
            return new Platform(
                    blank(minSupported) ? DEFAULT.minSupported() : minSupported.trim(),
                    blank(latest) ? DEFAULT.latest() : latest.trim(),
                    storeUrl == null ? "" : storeUrl.trim(),
                    blank(message) ? null : message.trim());
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }
    }

    public Platform forPlatform(AppPlatform platform) {
        Platform configured = null;
        if (versions != null) {
            configured = platform == AppPlatform.ANDROID ? versions.android() : versions.ios();
        }
        return (configured == null ? Platform.DEFAULT : configured).normalised();
    }
}
