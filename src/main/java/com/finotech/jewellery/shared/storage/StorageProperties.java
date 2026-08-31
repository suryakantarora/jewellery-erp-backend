package com.finotech.jewellery.shared.storage;

import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * File storage settings bound from {@code jewellery.storage.*}.
 *
 * @param maxFileSizeBytes refused above this, before anything is written
 * @param allowedContentTypes an allow-list rather than a deny-list: anything not
 *                            named here is refused, so a new upload type is a
 *                            deliberate decision
 */
@ConfigurationProperties(prefix = "jewellery.storage")
public record StorageProperties(String endpoint,
                                String bucket,
                                String localPath,
                                long maxFileSizeBytes,
                                List<String> allowedContentTypes) {

    private static final Set<String> DEFAULT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf");

    public StorageProperties {
        if (bucket == null || bucket.isBlank()) {
            bucket = "jewellery";
        }
        if (localPath == null || localPath.isBlank()) {
            localPath = "./storage";
        }
        if (maxFileSizeBytes <= 0) {
            maxFileSizeBytes = 10L * 1024 * 1024;
        }
        if (allowedContentTypes == null || allowedContentTypes.isEmpty()) {
            allowedContentTypes = List.copyOf(DEFAULT_TYPES);
        }
    }

    public boolean isAllowed(String contentType) {
        return contentType != null && allowedContentTypes.contains(contentType.toLowerCase());
    }
}
