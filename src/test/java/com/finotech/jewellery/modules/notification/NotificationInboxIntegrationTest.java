package com.finotech.jewellery.modules.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finotech.jewellery.IntegrationTestBase;
import com.finotech.jewellery.TestSecurity;
import com.finotech.jewellery.modules.notification.application.service.NotificationInboxService;
import com.finotech.jewellery.modules.notification.domain.entity.Notification;
import com.finotech.jewellery.modules.notification.domain.enums.NotificationChannel;
import com.finotech.jewellery.modules.notification.domain.enums.NotificationStatus;
import com.finotech.jewellery.modules.notification.domain.enums.RecipientType;
import com.finotech.jewellery.modules.notification.infrastructure.repository.NotificationReadRepository;
import com.finotech.jewellery.modules.notification.infrastructure.repository.NotificationRepository;
import com.finotech.jewellery.shared.exception.ForbiddenException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

/**
 * The staff inbox is a privacy boundary, so these are scoping tests first and
 * feature tests second.
 *
 * <p>Two real defects are pinned here. The inbox originally filtered on
 * {@code recipientId} alone, which silently hid every operational event: those
 * are queued once per branch with a null recipient, so nothing ever reached a
 * staff user. The fix introduced a second defect — read state was a column on
 * the shared broadcast row, so one person opening a message cleared everyone
 * else's badge.
 */
class NotificationInboxIntegrationTest extends IntegrationTestBase {

    private static final UUID BRANCH_A = UUID.randomUUID();
    private static final UUID BRANCH_B = UUID.randomUUID();

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationReadRepository readRepository;
    @Autowired private NotificationInboxService inboxService;

    /**
     * Each test asserts on exact inbox sizes, so rows must not survive from the
     * previous one. Reads go first: the notification they point at is what the
     * foreign key holds them to.
     */
    @BeforeEach
    void clearNotifications() {
        readRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    @AfterEach
    void clearAuthentication() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("a branch broadcast reaches everyone in that branch and nobody outside it")
    void broadcastIsScopedToItsBranch() {
        queue(RecipientType.USER, null, BRANCH_A, "Transfer received");

        UUID inBranch = UUID.randomUUID();
        TestSecurity.authenticateAs(inBranch, Set.of(), Set.of(BRANCH_A));
        assertThat(inboxService.inbox(false, Pageable.ofSize(50)).getContent())
                .as("a broadcast with no recipient id must still reach the branch's staff")
                .hasSize(1);

        TestSecurity.authenticateAs(UUID.randomUUID(), Set.of(), Set.of(BRANCH_B));
        assertThat(inboxService.inbox(false, Pageable.ofSize(50)).getContent())
                .as("another branch's staff must not see it")
                .isEmpty();
    }

    @Test
    @DisplayName("a directly addressed message reaches only its recipient")
    void directMessageIsPrivate() {
        UUID owner = UUID.randomUUID();
        queue(RecipientType.USER, owner, null, "Private");

        TestSecurity.authenticateAs(owner, Set.of(), Set.of(BRANCH_A));
        assertThat(inboxService.inbox(false, Pageable.ofSize(50)).getContent()).hasSize(1);

        TestSecurity.authenticateAs(UUID.randomUUID(), Set.of(), Set.of(BRANCH_A));
        assertThat(inboxService.inbox(false, Pageable.ofSize(50)).getContent())
                .as("a colleague in the same branch must not read a personal message")
                .isEmpty();
    }

    @Test
    @DisplayName("customer mail never appears in a staff inbox")
    void customerMailIsExcluded() {
        queue(RecipientType.CUSTOMER, UUID.randomUUID(), BRANCH_A, "Your purchase is complete");

        TestSecurity.authenticateAs(UUID.randomUUID(), Set.of(), Set.of(BRANCH_A));
        assertThat(inboxService.inbox(false, Pageable.ofSize(50)).getContent()).isEmpty();
    }

    @Test
    @DisplayName("one user reading a broadcast leaves a colleague's unread count alone")
    void readStateIsPerUser() {
        queue(RecipientType.USER, null, BRANCH_A, "Transfer received");

        UUID reader = UUID.randomUUID();
        UUID colleague = UUID.randomUUID();

        TestSecurity.authenticateAs(reader, Set.of(), Set.of(BRANCH_A));
        assertThat(inboxService.unreadCount()).isEqualTo(1);
        inboxService.markAllRead();
        assertThat(inboxService.unreadCount()).isZero();

        TestSecurity.authenticateAs(colleague, Set.of(), Set.of(BRANCH_A));
        assertThat(inboxService.unreadCount())
                .as("read state belongs to a (notification, user) pair, not to the row")
                .isEqualTo(1);
        assertThat(inboxService.inbox(false, Pageable.ofSize(50)).getContent().get(0).getReadAt())
                .isNull();
    }

    @Test
    @DisplayName("marking a message read that is not yours is refused")
    void cannotReadSomeoneElsesMessage() {
        UUID id = queue(RecipientType.USER, UUID.randomUUID(), null, "Private");

        TestSecurity.authenticateAs(UUID.randomUUID(), Set.of(), Set.of(BRANCH_A));
        assertThatThrownBy(() -> inboxService.markRead(id))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("a super administrator sees broadcasts despite having no branches")
    void superAdminSeesEveryBranch() {
        queue(RecipientType.USER, null, BRANCH_A, "Transfer received");

        TestSecurity.authenticateAsSuperAdmin();
        assertThat(inboxService.inbox(false, Pageable.ofSize(50)).getContent())
                .as("an empty branch set means 'every branch' for a super admin, not 'none'")
                .hasSize(1);
    }

    private UUID queue(RecipientType type, UUID recipientId, UUID branchId, String body) {
        Notification notification = new Notification();
        notification.setEventType("ITEM_TRANSFERRED");
        notification.setChannel(NotificationChannel.IN_APP);
        notification.setRecipientType(type);
        notification.setRecipientId(recipientId);
        notification.setBranchId(branchId);
        notification.setBody(body);
        notification.setStatus(NotificationStatus.PENDING);
        return notificationRepository.saveAndFlush(notification).getId();
    }
}
