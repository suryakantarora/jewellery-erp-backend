package com.finotech.jewellery.modules.notification.infrastructure.repository;

import com.finotech.jewellery.modules.notification.domain.entity.Notification;
import com.finotech.jewellery.modules.notification.domain.enums.NotificationChannel;
import com.finotech.jewellery.modules.notification.domain.enums.NotificationStatus;
import java.util.Collection;
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

    /**
     * One staff user's inbox.
     *
     * <p>Two kinds of message qualify: those addressed to the user by id, and
     * branch broadcasts (operational events such as a transfer or a low-stock
     * warning, which have no single owner) for the branches the user works in.
     * Customer-facing messages are excluded outright — they are somebody else's
     * mail, and this endpoint must never expose them. Only IN_APP rows qualify:
     * an event with both an IN_APP and a PUSH template queues two rows, and the
     * PUSH one is a delivery record for the phone, not a second inbox entry.
     *
     * <p>{@code allBranches} exists for super administrators, whose branch set
     * is empty precisely because they may act everywhere; without it an empty
     * set would read as "no branches" and hide every broadcast from them.
     */
    @Query("""
            select n from Notification n
            where n.recipientType = com.finotech.jewellery.modules.notification.domain.enums.RecipientType.USER
              and n.channel = com.finotech.jewellery.modules.notification.domain.enums.NotificationChannel.IN_APP
              and (n.recipientId = :userId
                   or (n.recipientId is null
                       and (:allBranches = true or n.branchId in :branchIds)))
              and (:unreadOnly = false
                   or n.id not in (select r.id.notificationId from NotificationRead r
                                   where r.id.userId = :userId))
            order by n.createdAt desc
            """)
    Page<Notification> findInbox(@Param("userId") UUID userId,
                                 @Param("branchIds") Collection<UUID> branchIds,
                                 @Param("allBranches") boolean allBranches,
                                 @Param("unreadOnly") boolean unreadOnly,
                                 Pageable pageable);

    @Query("""
            select count(n) from Notification n
            where n.recipientType = com.finotech.jewellery.modules.notification.domain.enums.RecipientType.USER
              and n.channel = com.finotech.jewellery.modules.notification.domain.enums.NotificationChannel.IN_APP
              and (n.recipientId = :userId
                   or (n.recipientId is null
                       and (:allBranches = true or n.branchId in :branchIds)))
              and n.id not in (select r.id.notificationId from NotificationRead r
                               where r.id.userId = :userId)
            """)
    long countUnread(@Param("userId") UUID userId,
                     @Param("branchIds") Collection<UUID> branchIds,
                     @Param("allBranches") boolean allBranches);

    long countByStatus(NotificationStatus status);
}
