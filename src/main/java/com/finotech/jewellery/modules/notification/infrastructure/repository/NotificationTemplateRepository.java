package com.finotech.jewellery.modules.notification.infrastructure.repository;

import com.finotech.jewellery.modules.notification.domain.entity.NotificationTemplate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    /** Every active template registered for an event, across channels. */
    List<NotificationTemplate> findAllByEventTypeAndActiveTrue(String eventType);

    List<NotificationTemplate> findAllByOrderByEventTypeAscChannelAsc();
}
