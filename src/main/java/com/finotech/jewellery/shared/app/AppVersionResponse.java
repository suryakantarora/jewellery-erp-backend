package com.finotech.jewellery.shared.app;

/**
 * @param forceUpdate true when the caller's {@code current} version is below
 *                    {@code minSupported}; false when it is supported or when
 *                    no parseable version was sent
 */
public record AppVersionResponse(AppPlatform platform,
                                 String minSupported,
                                 String latest,
                                 String storeUrl,
                                 String message,
                                 boolean forceUpdate,
                                 boolean updateAvailable) {
}
