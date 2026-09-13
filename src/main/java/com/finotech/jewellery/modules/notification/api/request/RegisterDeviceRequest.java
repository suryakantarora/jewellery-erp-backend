package com.finotech.jewellery.modules.notification.api.request;

import com.finotech.jewellery.modules.notification.domain.enums.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The FCM registration token a signed-in app instance wants push sent to. */
public record RegisterDeviceRequest(@NotBlank @Size(max = 512) String token,
                                    @NotNull DevicePlatform platform,
                                    @Size(max = 50) String appVersion) {
}
