package com.finotech.jewellery.modules.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.finotech.jewellery.CommerceFixture;
import com.finotech.jewellery.IntegrationTestBase;
import com.finotech.jewellery.TestSecurity;
import com.finotech.jewellery.modules.identity.api.request.CreateUserRequest;
import com.finotech.jewellery.modules.identity.api.request.RoleRequest;
import com.finotech.jewellery.modules.identity.api.response.PermissionResponse;
import com.finotech.jewellery.modules.identity.application.service.RoleService;
import com.finotech.jewellery.modules.identity.application.service.UserService;
import com.finotech.jewellery.modules.inventory.api.request.CreateMovementRequest;
import com.finotech.jewellery.modules.inventory.api.response.MovementResponse;
import com.finotech.jewellery.modules.inventory.application.service.InventoryMovementService;
import com.finotech.jewellery.modules.inventory.domain.enums.MovementStatus;
import com.finotech.jewellery.modules.inventory.domain.enums.MovementType;
import com.finotech.jewellery.modules.notification.api.request.RegisterDeviceRequest;
import com.finotech.jewellery.modules.notification.application.service.NotificationDeviceService;
import com.finotech.jewellery.modules.notification.application.service.NotificationInboxService;
import com.finotech.jewellery.modules.notification.domain.entity.Notification;
import com.finotech.jewellery.modules.notification.domain.entity.NotificationDevice;
import com.finotech.jewellery.modules.notification.domain.enums.DevicePlatform;
import com.finotech.jewellery.modules.notification.domain.enums.NotificationChannel;
import com.finotech.jewellery.modules.notification.domain.enums.NotificationStatus;
import com.finotech.jewellery.modules.notification.domain.enums.RecipientType;
import com.finotech.jewellery.modules.notification.infrastructure.repository.NotificationDeviceRepository;
import com.finotech.jewellery.modules.notification.infrastructure.repository.NotificationRepository;
import com.finotech.jewellery.modules.organization.api.request.BranchRequest;
import com.finotech.jewellery.modules.organization.api.request.LocationRequest;
import com.finotech.jewellery.modules.organization.application.service.OrganizationService;
import com.finotech.jewellery.modules.organization.domain.enums.LocationType;
import com.finotech.jewellery.shared.event.DomainEventPublisher;
import com.finotech.jewellery.shared.event.DomainEvents;
import com.finotech.jewellery.shared.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Staff-directed notifications: the right people, and only those people.
 *
 * <p>Until now every operational message was a branch broadcast. "A transfer is
 * awaiting <em>your</em> approval" needs a recipient, and resolving one wrongly
 * is a privacy problem as much as a UX one, so these pin the fan-out: who is
 * told, who is not, and that a phone (PUSH row) never doubles as an inbox entry.
 *
 * <p>Users are real rows with roles, permissions and branches, because the
 * recipient query joins all four; a mocked directory would prove nothing.
 */
@Import(CommerceFixture.class)
class StaffNotificationIntegrationTest extends IntegrationTestBase {

    @Autowired private CommerceFixture fixture;
    @Autowired private OrganizationService organizationService;
    @Autowired private RoleService roleService;
    @Autowired private UserService userService;
    @Autowired private InventoryMovementService movementService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationDeviceRepository deviceRepository;
    @Autowired private NotificationDeviceService deviceService;
    @Autowired private NotificationInboxService inboxService;
    @Autowired private DomainEventPublisher publisher;
    @Autowired private TransactionTemplate transactionTemplate;

    private CommerceFixture.World world;
    private UUID vaultId;
    private UUID otherBranchId;
    private UUID approverRoleId;
    private UUID viewerRoleId;
    private String tag;

    @BeforeEach
    void setUp() {
        TestSecurity.authenticateAsSuperAdmin();
        tag = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        world = fixture.create(new BigDecimal("1000000"), new BigDecimal("50000"), null, null);

        // A vault forces approval (and dual authorisation) on any movement into it.
        vaultId = organizationService.createLocation(new LocationRequest(world.branchId(), null,
                "VLT" + tag, "Vault", LocationType.VAULT, true, null, null)).id();

        // Another branch of the same company: staff may be granted both, which
        // they could not be across companies.
        otherBranchId = organizationService.createBranch(new BranchRequest(world.companyId(),
                "OB" + tag, "Other Branch", false, null, null, null, null, null, null)).id();

        approverRoleId = role("APR", "INVENTORY_TRANSFER_APPROVE");
        viewerRoleId = role("RPT", "REPORT_VIEW");
    }

    @AfterEach
    void clearAuthentication() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("a movement needing approval reaches each approver in the branch and nobody else")
    void awaitingApprovalFansOutToBranchApprovers() {
        UUID approverA1 = user("a1", approverRoleId, world.branchId());
        UUID approverA2 = user("a2", approverRoleId, world.branchId());
        UUID approverB = user("b1", approverRoleId, otherBranchId);
        UUID viewerA = user("v1", viewerRoleId, world.branchId());

        MovementResponse movement = raiseVaultMovement("creator" + tag);

        List<Notification> queued = rowsFor("TRANSFER_AWAITING_APPROVAL", movement.id(),
                NotificationChannel.IN_APP);
        assertThat(queued).extracting(Notification::getRecipientId)
                .as("one IN_APP row per approver who works in the branch")
                .containsExactlyInAnyOrder(approverA1, approverA2);
        assertThat(queued).extracting(Notification::getRecipientId)
                .doesNotContain(approverB, viewerA);
        assertThat(queued).allSatisfy(n -> {
            assertThat(n.getRecipientType()).isEqualTo(RecipientType.USER);
            assertThat(n.getReferenceType()).isEqualTo("InventoryMovement");
            assertThat(n.getSubject()).isEqualTo("Transfer awaiting your approval");
            assertThat(n.getBody()).contains(movement.referenceNumber()).contains("Vault");
        });
        assertThat(rowsFor("TRANSFER_AWAITING_APPROVAL", movement.id(), NotificationChannel.PUSH))
                .as("a PUSH row per approver too, for the phone")
                .hasSize(2);
    }

    @Test
    @DisplayName("approval tells the creator, and a second signature is asked of the other approver")
    void approvalNotifiesCreatorByUserId() {
        String creatorName = "creator" + tag;
        UUID creatorId = user(creatorName, viewerRoleId, world.branchId());
        UUID approver1 = user("p1", approverRoleId, world.branchId());
        UUID approver2 = user("p2", approverRoleId, world.branchId());

        MovementResponse movement = raiseVaultMovement(creatorName);

        authenticateAs(approver1, "p1" + tag);
        MovementResponse afterFirst = movementService.approve(movement.id());
        assertThat(afterFirst.status()).isEqualTo(MovementStatus.PENDING_APPROVAL);

        List<Notification> secondAsk = rowsFor("TRANSFER_AWAITING_APPROVAL", movement.id(),
                NotificationChannel.IN_APP).stream()
                .filter(n -> n.getBody().contains(movement.referenceNumber()))
                .toList();
        // Creation asked both; the first signature asks only the other approver.
        assertThat(secondAsk).filteredOn(n -> approver2.equals(n.getRecipientId())).hasSize(2);
        assertThat(secondAsk).filteredOn(n -> approver1.equals(n.getRecipientId()))
                .as("the first approver is not asked to sign again")
                .hasSize(1);

        authenticateAs(approver2, "p2" + tag);
        MovementResponse approved = movementService.approve(movement.id());
        assertThat(approved.status()).isEqualTo(MovementStatus.APPROVED);

        List<Notification> decided = rowsFor("TRANSFER_APPROVED", movement.id(),
                NotificationChannel.IN_APP);
        assertThat(decided).hasSize(1);
        assertThat(decided.get(0).getRecipientId())
                .as("created_by is a username; it resolves to the creator's user id")
                .isEqualTo(creatorId);
        assertThat(decided.get(0).getSubject()).isEqualTo("Transfer approved");
    }

    @Test
    @DisplayName("registering a token already held by someone else re-owns it")
    void deviceRegistrationReownsToken() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String token = "fcm-" + tag;

        TestSecurity.authenticateAs(first, Set.of(), Set.of(world.branchId()));
        NotificationDevice registered = deviceService.register(
                new RegisterDeviceRequest(token, DevicePlatform.ANDROID, "1.0.0"));
        assertThat(registered.getUserId()).isEqualTo(first);

        TestSecurity.authenticateAs(second, Set.of(), Set.of(world.branchId()));
        deviceService.register(new RegisterDeviceRequest(token, DevicePlatform.IOS, "1.1.0"));

        NotificationDevice owned = deviceRepository.findByToken(token).orElseThrow();
        assertThat(owned.getId()).as("same row, not a duplicate").isEqualTo(registered.getId());
        assertThat(owned.getUserId()).isEqualTo(second);
        assertThat(owned.getPlatform()).isEqualTo(DevicePlatform.IOS);
        assertThat(deviceRepository.findAllByUserId(first)).isEmpty();

        // The previous owner cannot remove it; the current one can; both calls succeed.
        TestSecurity.authenticateAs(first, Set.of(), Set.of(world.branchId()));
        deviceService.unregister(token);
        assertThat(deviceRepository.findByToken(token)).isPresent();

        TestSecurity.authenticateAs(second, Set.of(), Set.of(world.branchId()));
        deviceService.unregister(token);
        deviceService.unregister(token);
        assertThat(deviceRepository.findByToken(token)).isEmpty();
    }

    @Test
    @DisplayName("PUSH rows are delivery records, not inbox entries")
    void inboxExcludesPushRows() {
        UUID me = UUID.randomUUID();
        queue(me, NotificationChannel.IN_APP);
        queue(me, NotificationChannel.PUSH);

        TestSecurity.authenticateAs(me, Set.of(), Set.of(world.branchId()));
        List<Notification> inbox = inboxService.inbox(false, Pageable.ofSize(50)).getContent();
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(inboxService.unreadCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a sale at the compliance threshold alerts the branch's report viewers")
    void highValueSaleFansOutToReportViewers() {
        UUID managerA = user("m1", viewerRoleId, world.branchId());
        UUID managerB = user("m2", viewerRoleId, otherBranchId);
        UUID approverA = user("m3", approverRoleId, world.branchId());
        UUID saleId = UUID.randomUUID();

        // Listeners run after commit, so the event must be raised inside a transaction.
        transactionTemplate.executeWithoutResult(tx -> publisher.publish(
                new DomainEvents.SaleCompleted(saleId, world.customerId(), world.branchId(),
                        "INV-" + tag, new BigDecimal("100000000"), 1)));

        List<Notification> alerts = rowsFor("HIGH_VALUE_SALE", saleId, NotificationChannel.IN_APP);
        assertThat(alerts).extracting(Notification::getRecipientId)
                .containsExactly(managerA)
                .doesNotContain(managerB, approverA);
        assertThat(alerts.get(0).getReferenceType()).isEqualTo("Sale");
        assertThat(alerts.get(0).getBody()).contains("INV-" + tag).contains("LAK");
    }

    // ---------- helpers ----------

    private MovementResponse raiseVaultMovement(String creatorUsername) {
        UUID itemId = fixture.availableItem(world, new BigDecimal("8.500"));
        // created_by is filled from the security context's username.
        authenticateAs(UUID.randomUUID(), creatorUsername);
        MovementResponse movement = movementService.create(new CreateMovementRequest(
                MovementType.TRANSFER, world.locationId(), vaultId, List.of(itemId), null), null);
        assertThat(movement.status()).isEqualTo(MovementStatus.PENDING_APPROVAL);
        TestSecurity.authenticateAsSuperAdmin();
        return movement;
    }

    private UUID role(String prefix, String permissionCode) {
        UUID permissionId = roleService.listPermissions().stream()
                .filter(p -> p.code().equals(permissionCode))
                .map(PermissionResponse::id)
                .findFirst().orElseThrow();
        return roleService.create(new RoleRequest(prefix + tag, prefix + " " + tag, null,
                Set.of(permissionId))).id();
    }

    private UUID user(String name, UUID roleId, UUID branchId) {
        String username = name.contains(tag) ? name : name + tag;
        return userService.create(new CreateUserRequest(username, "Password123!x", name,
                null, null, null, branchId, Set.of(roleId), Set.of(branchId), null)).id();
    }

    /** A principal with a chosen username, so audit columns name a real user. */
    private static void authenticateAs(UUID userId, String username) {
        AuthenticatedUser user = new AuthenticatedUser(userId, username,
                Set.of("INVENTORY_TRANSFER", "INVENTORY_TRANSFER_APPROVE"), Set.of(), true, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.authorities()));
    }

    private List<Notification> rowsFor(String eventType, UUID referenceId,
                                       NotificationChannel channel) {
        return notificationRepository.search(null, channel, eventType, null, Pageable.ofSize(200))
                .getContent().stream()
                .filter(n -> String.valueOf(referenceId).equals(n.getReferenceId()))
                .toList();
    }

    private void queue(UUID userId, NotificationChannel channel) {
        Notification notification = new Notification();
        notification.setEventType("TRANSFER_APPROVED");
        notification.setChannel(channel);
        notification.setRecipientType(RecipientType.USER);
        notification.setRecipientId(userId);
        notification.setBranchId(world.branchId());
        notification.setBody("Transfer approved");
        notification.setStatus(NotificationStatus.PENDING);
        notificationRepository.saveAndFlush(notification);
    }
}
