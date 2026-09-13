package com.finotech.jewellery.modules.notification.application.service;

import com.finotech.jewellery.modules.notification.api.request.RegisterDeviceRequest;
import com.finotech.jewellery.modules.notification.domain.entity.NotificationDevice;
import com.finotech.jewellery.modules.notification.infrastructure.repository.NotificationDeviceRepository;
import com.finotech.jewellery.shared.security.AuthenticatedUser;
import com.finotech.jewellery.shared.security.SecurityUtils;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Push device registration, scoped to the caller like the inbox.
 *
 * <p>The user id always comes from the security context. A request body that
 * named a user would let anyone subscribe their phone to a colleague's mail.
 */
@Service
@RequiredArgsConstructor
public class NotificationDeviceService {

    private final NotificationDeviceRepository deviceRepository;

    /**
     * Upserts by token. A token already held by another user is re-owned: the
     * token identifies the handset, and the handset now belongs to the caller.
     */
    @Transactional
    public NotificationDevice register(RegisterDeviceRequest request) {
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        String token = request.token().trim();
        NotificationDevice device = deviceRepository.findByToken(token)
                .orElseGet(NotificationDevice::new);
        device.setUserId(user.userId());
        device.setToken(token);
        device.setPlatform(request.platform());
        device.setAppVersion(request.appVersion());
        device.setLastSeenAt(Instant.now());
        return deviceRepository.save(device);
    }

    /**
     * Removes the token if the caller owns it. Idempotent: an unknown token, or
     * one that now belongs to somebody else, is simply not the caller's to
     * remove and the call still succeeds.
     */
    @Transactional
    public void unregister(String token) {
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        deviceRepository.findByToken(token.trim())
                .filter(device -> user.userId().equals(device.getUserId()))
                .ifPresent(deviceRepository::delete);
    }
}
