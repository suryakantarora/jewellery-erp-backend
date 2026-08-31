package com.finotech.jewellery.modules.notification.infrastructure.repository;

import com.finotech.jewellery.modules.notification.domain.entity.Notification;
import com.finotech.jewellery.modules.notification.domain.enums.NotificationChannel;
import com.finotech.jewellery.modules.notification.domain.enums.NotificationStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * The dispatch queue: anything still worth attempting, oldest first so
     * messages go out in the order the business events happened.
     */
    @Query("""
            select n from Notification n
            where n.status in (com.finotech.jewellery.modules.notification.domain.enums.NotificationStatus.PENDING,
                               com.finotech.jewellery.modules.notification.domain.enums.NotificationStatus.FAILED)
              and n.attemptCount < :maxAttempts
            order by n.createdAt asc
            """)
    List<Notification> findDispatchable(@Param("maxAttempts") int maxAttempts, Pageable pageable);

    @Query("""
            select n from Notification n
            where (:status is null or n.status = :status)
              and (:channel is null or n.channel = :channel)
              and (cast(:eventType as string) is null or n.eventType = :eventType)
              and (:recipientId is null or n.recipientId = :recipientId)
            order by n.createdAt desc
            """)
    Page<Notification> search(@Param("status") NotificationStatus status,
                              @Param("channel") NotificationChannel channel,
                              @Param("eventType") String eventType,
                              @Param("recipientId") UUID recipientId,
                              Pageable pageable);

    long countByStatus(NotificationStatus status);
}
