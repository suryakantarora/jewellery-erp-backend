package com.finotech.jewellery.modules.notification.application.service;

import com.finotech.jewellery.modules.notification.domain.entity.Notification;
import com.finotech.jewellery.modules.notification.domain.entity.NotificationRead;
import com.finotech.jewellery.modules.notification.infrastructure.repository.NotificationReadRepository;
import com.finotech.jewellery.modules.notification.infrastructure.repository.NotificationRepository;
import com.finotech.jewellery.shared.exception.ForbiddenException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.security.AuthenticatedUser;
import com.finotech.jewellery.shared.security.SecurityUtils;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The staff inbox: notifications addressed to the caller.
 *
 * <p>Scope is taken from the security context, never from a request parameter.
 * The existing {@code GET /notifications} search accepts an arbitrary
 * {@code recipientId}, which is correct for an administrator auditing the
 * queue but would let any holder of that permission read a colleague's mail if
 * it backed the app's inbox. This service exists so the app never needs it.
 */
@Service
@RequiredArgsConstructor
public class NotificationInboxService {

    /**
     * Stands in for an empty branch set. Hibernate cannot render an empty
     * {@code IN ()} list, and a super administrator legitimately has no
     * branches; the query ignores this value whenever {@code allBranches} is
     * true, and it can never match a real branch.
     */
    private static final UUID NO_BRANCH = new UUID(0L, 0L);

    private final NotificationRepository notificationRepository;
    private final NotificationReadRepository readRepository;

    @Transactional(readOnly = true)
    public Page<Notification> inbox(boolean unreadOnly, Pageable pageable) {
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        Page<Notification> page = notificationRepository.findInbox(user.userId(), branchScope(user),
                user.superAdmin(), unreadOnly, pageable);
        applyReadState(page.getContent(), user.userId());
        return page;
    }

    /**
     * Stamps each row with this caller's read time in one extra query, rather
     * than one per row.
     */
    private void applyReadState(List<Notification> notifications, UUID userId) {
        if (notifications.isEmpty()) {
            return;
        }
        Set<UUID> readIds = new HashSet<>(readRepository.findReadIds(userId,
                notifications.stream().map(Notification::getId).toList()));
        // A read timestamp per row would cost a second column in the projection
        // for no benefit: the app only ever asks whether it is read.
        Instant marker = Instant.now();
        notifications.forEach(n -> n.setReadAt(readIds.contains(n.getId()) ? marker : null));
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        return notificationRepository.countUnread(user.userId(), branchScope(user),
                user.superAdmin());
    }

    /** Marks one message read, provided it is actually addressed to the caller. */
    @Transactional
    public Notification markRead(UUID notificationId) {
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        if (!isAddressedTo(notification, user)) {
            throw new ForbiddenException("This notification is not addressed to you");
        }
        Instant readAt = Instant.now();
        // Idempotent: re-reading keeps the first timestamp rather than moving it.
        if (!readRepository.existsById(new NotificationRead.Id(notificationId, user.userId()))) {
            readRepository.save(new NotificationRead(notificationId, user.userId(), readAt));
        }
        notification.setReadAt(readAt);
        return notification;
    }

    /** Clears the badge in one call, so the app does not issue one POST per row. */
    @Transactional
    public int markAllRead() {
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        Page<Notification> unread = notificationRepository.findInbox(user.userId(),
                branchScope(user), user.superAdmin(), true, Pageable.ofSize(500));
        Instant readAt = Instant.now();
        readRepository.saveAll(unread.getContent().stream()
                .map(n -> new NotificationRead(n.getId(), user.userId(), readAt))
                .toList());
        return unread.getNumberOfElements();
    }

    private boolean isAddressedTo(Notification notification, AuthenticatedUser user) {
        if (notification.getRecipientType() != com.finotech.jewellery.modules.notification
                .domain.enums.RecipientType.USER
                || notification.getChannel() != com.finotech.jewellery.modules.notification
                .domain.enums.NotificationChannel.IN_APP) {
            return false;
        }
        if (user.userId().equals(notification.getRecipientId())) {
            return true;
        }
        // A broadcast belongs to everyone who works in that branch.
        return notification.getRecipientId() == null
                && user.hasAccessToBranch(notification.getBranchId());
    }

    private Collection<UUID> branchScope(AuthenticatedUser user) {
        return user.branchIds().isEmpty() ? Set.of(NO_BRANCH) : user.branchIds();
    }
}
