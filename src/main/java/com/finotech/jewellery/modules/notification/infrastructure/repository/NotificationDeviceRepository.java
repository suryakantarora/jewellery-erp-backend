package com.finotech.jewellery.modules.notification.infrastructure.repository;

import com.finotech.jewellery.modules.notification.domain.entity.NotificationDevice;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeviceRepository extends JpaRepository<NotificationDevice, UUID> {

    Optional<NotificationDevice> findByToken(String token);

    List<NotificationDevice> findAllByUserId(UUID userId);

    void deleteAllByTokenIn(Collection<String> tokens);
}
