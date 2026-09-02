package com.finotech.jewellery.modules.notification.infrastructure.repository;

import com.finotech.jewellery.modules.notification.domain.entity.NotificationRead;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationReadRepository
        extends JpaRepository<NotificationRead, NotificationRead.Id> {

    @Query("""
            select r.id.notificationId from NotificationRead r
            where r.id.userId = :userId and r.id.notificationId in :notificationIds
            """)
    List<UUID> findReadIds(@Param("userId") UUID userId,
                           @Param("notificationIds") Collection<UUID> notificationIds);

    @Query("select r.id.notificationId from NotificationRead r where r.id.userId = :userId")
    List<UUID> findAllReadIds(@Param("userId") UUID userId);
}
