package com.finotech.jewellery.modules.notification.api.response;

import com.finotech.jewellery.modules.notification.domain.entity.NotificationDevice;
import com.finotech.jewellery.modules.notification.domain.enums.DevicePlatform;
import java.time.Instant;
import java.util.UUID;

public record DeviceResponse(UUID id, String token, DevicePlatform platform, String appVersion,
                             Instant lastSeenAt) {

    public static DeviceResponse from(NotificationDevice d) {
        return new DeviceResponse(d.getId(), d.getToken(), d.getPlatform(), d.getAppVersion(),
                d.getLastSeenAt());
    }
}
