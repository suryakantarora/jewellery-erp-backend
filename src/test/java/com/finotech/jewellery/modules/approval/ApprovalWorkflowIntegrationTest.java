package com.finotech.jewellery.modules.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finotech.jewellery.CommerceFixture;
import com.finotech.jewellery.IntegrationTestBase;
import com.finotech.jewellery.TestSecurity;
import com.finotech.jewellery.modules.approval.api.request.ApprovalRequests.DecisionRequest;
import com.finotech.jewellery.modules.approval.api.response.ApprovalResponses.ApprovalItemResponse;
import com.finotech.jewellery.modules.approval.api.response.ApprovalResponses.DecisionResponse;
import com.finotech.jewellery.modules.approval.api.response.ApprovalResponses.InformationRequestResponse;
import com.finotech.jewellery.modules.approval.application.service.ApprovalService;
import com.finotech.jewellery.modules.approval.domain.enums.ApprovalDecision;
import com.finotech.jewellery.modules.approval.domain.enums.ApprovalType;
import com.finotech.jewellery.modules.inventory.api.request.CreateMovementRequest;
import com.finotech.jewellery.modules.inventory.application.service.InventoryMovementService;
import com.finotech.jewellery.modules.inventory.domain.enums.MovementStatus;
import com.finotech.jewellery.modules.inventory.domain.enums.MovementType;
import com.finotech.jewellery.modules.organization.api.request.BranchRequest;
import com.finotech.jewellery.modules.organization.api.request.CompanyRequest;
import com.finotech.jewellery.modules.organization.api.request.LocationRequest;
import com.finotech.jewellery.modules.organization.application.service.OrganizationService;
import com.finotech.jewellery.modules.organization.domain.enums.LocationType;
import com.finotech.jewellery.modules.sales.api.request.DiscountRequestRequests.CreateDiscountRequest;
import com.finotech.jewellery.modules.sales.api.response.DiscountRequestResponse;
import com.finotech.jewellery.modules.sales.application.service.DiscountRequestService;
import com.finotech.jewellery.modules.sales.domain.enums.DiscountRequestStatus;
import com.finotech.jewellery.shared.exception.BusinessException;
import com.finotech.jewellery.shared.exception.ErrorCode;
import com.finotech.jewellery.shared.exception.ForbiddenException;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * The unified approvals queue: scoped to what the caller may approve, able to
 * ask a question without deciding, and safe to retry.
 */
@Import(CommerceFixture.class)
class ApprovalWorkflowIntegrationTest extends IntegrationTestBase {

    private static final Set<String> APPROVER_PERMS =
            Set.of("INVENTORY_TRANSFER_APPROVE", "DISCOUNT_APPROVE");

    @Autowired private CommerceFixture fixture;
    @Autowired private OrganizationService organizationService;
    @Autowired private InventoryMovementService movementService;
    @Autowired private DiscountRequestService discountRequestService;
    @Autowired private ApprovalService approvalService;

    private CommerceFixture.World world;
    private UUID otherBranchId;
    private UUID otherLocationId;
    private UUID requesterId;

    @BeforeEach
    void setUp() {
        TestSecurity.authenticateAsSuperAdmin();
        world = fixture.create(new BigDecimal("2000000"), new BigDecimal("50000"),
                new BigDecimal("2.0"), null);
        String tag = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        var company = organizationService.createCompany(new CompanyRequest(
                "CO" + tag, "Other Co", null, null, null, "LAK", null, null, null, null, null));
        otherBranchId = organizationService.createBranch(new BranchRequest(
                company.id(), "BR" + tag, "Other Branch", false, null, null, null, null, null, null)).id();
        otherLocationId = organizationService.createLocation(new LocationRequest(
                otherBranchId, null, "LOC" + tag, "Other Showroom", LocationType.SHOWROOM, false,
                null, null)).id();
        requesterId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    // ---------- fixtures ----------

    /** A cross-branch transfer into the fixture branch, which always needs approval. */
    private UUID pendingTransfer() {
        TestSecurity.authenticateAsSuperAdmin();
        UUID itemId = fixture.availableItem(world, new BigDecimal("8.000"));
        var movement = movementService.create(new CreateMovementRequest(MovementType.TRANSFER,
                world.locationId(), otherLocationId, List.of(itemId), null), null);
        assertThat(movement.status()).isEqualTo(MovementStatus.PENDING_APPROVAL);
        return movement.id();
    }

    private UUID pendingDiscountRequest() {
        TestSecurity.authenticateAs(requesterId, Set.of("DISCOUNT_REQUEST"), Set.of(world.branchId()));
        DiscountRequestResponse created = discountRequestService.create(new CreateDiscountRequest(
                world.branchId(), world.customerId(), null, null, new BigDecimal("10"), null, null,
                "Regular customer, wedding order"));
        assertThat(created.status()).isEqualTo(DiscountRequestStatus.PENDING);
        return created.id();
    }

    // ---------- pending list ----------

    @Test
    @DisplayName("the pending list holds only the types the caller may approve, in their branches")
    void pendingListIsScopedByPermissionAndBranch() {
        UUID transferId = pendingTransfer();
        UUID discountId = pendingDiscountRequest();

        // Holds both approve permissions and both branches: sees both.
        TestSecurity.authenticateAs(APPROVER_PERMS, Set.of(world.branchId(), otherBranchId));
        List<ApprovalItemResponse> all = approvalService.pending(null, null);
        assertThat(all).extracting(ApprovalItemResponse::id).contains(transferId, discountId);

        ApprovalItemResponse transferRow = all.stream()
                .filter(r -> r.id().equals(transferId)).findFirst().orElseThrow();
        assertThat(transferRow.type()).isEqualTo(ApprovalType.TRANSFER);
        assertThat(transferRow.reference()).startsWith("MOV-");
        assertThat(transferRow.summary()).contains("1 item").contains("→");
        assertThat(transferRow.branchName()).isEqualTo("Other Branch");
        assertThat(transferRow.awaitingSecondApproval()).isFalse();
        assertThat(transferRow.infoRequested()).isFalse();

        ApprovalItemResponse discountRow = all.stream()
                .filter(r -> r.id().equals(discountId)).findFirst().orElseThrow();
        assertThat(discountRow.type()).isEqualTo(ApprovalType.DISCOUNT);
        assertThat(discountRow.summary()).startsWith("10%");
        assertThat(discountRow.requestedBy()).isEqualTo("test-user-" + requesterId);

        // Only DISCOUNT_APPROVE: the transfer is not theirs to decide.
        TestSecurity.authenticateAs(Set.of("DISCOUNT_APPROVE"), Set.of(world.branchId(), otherBranchId));
        List<ApprovalItemResponse> discountOnly = approvalService.pending(null, null);
        assertThat(discountOnly).extracting(ApprovalItemResponse::id)
                .contains(discountId).doesNotContain(transferId);

        // Both permissions but no access to the destination branch: transfer hidden.
        TestSecurity.authenticateAs(APPROVER_PERMS, Set.of(world.branchId()));
        List<ApprovalItemResponse> fixtureBranchOnly = approvalService.pending(null, null);
        assertThat(fixtureBranchOnly).extracting(ApprovalItemResponse::id)
                .contains(discountId).doesNotContain(transferId);

        // The count endpoint agrees with the list.
        var count = approvalService.pendingCount(null);
        assertThat(count.byType().get(ApprovalType.DISCOUNT)).isGreaterThanOrEqualTo(1);
        assertThat(count.byType().get(ApprovalType.TRANSFER)).isZero();
        assertThat(count.total()).isEqualTo(fixtureBranchOnly.size());
    }

    // ---------- decisions ----------

    @Test
    @DisplayName("a transfer is approved through the unified path and a replay returns the same result")
    void approveTransferIdempotently() {
        UUID transferId = pendingTransfer();
        TestSecurity.authenticateAs(APPROVER_PERMS, Set.of(world.branchId(), otherBranchId));
        String key = "APR-" + UUID.randomUUID();

        DecisionResponse first = approvalService.decide(ApprovalType.TRANSFER, transferId,
                new DecisionRequest(ApprovalDecision.APPROVE, null), key);
        assertThat(first.status()).isEqualTo("APPROVED");
        assertThat(first.decidedBy()).isEqualTo("test-user");
        assertThat(movementService.get(transferId).status()).isEqualTo(MovementStatus.APPROVED);

        DecisionResponse replay = approvalService.decide(ApprovalType.TRANSFER, transferId,
                new DecisionRequest(ApprovalDecision.APPROVE, null), key);
        assertThat(replay.status()).isEqualTo("APPROVED");
        assertThat(replay.decidedAt().truncatedTo(ChronoUnit.MILLIS))
                .isEqualTo(first.decidedAt().truncatedTo(ChronoUnit.MILLIS));

        // Same key, different intent: refused rather than silently reinterpreted.
        assertThatThrownBy(() -> approvalService.decide(ApprovalType.TRANSFER, transferId,
                new DecisionRequest(ApprovalDecision.REJECT, "changed my mind"), key))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);

        // Without a key, approving again by the same user is tolerated, not a conflict.
        DecisionResponse again = approvalService.decide(ApprovalType.TRANSFER, transferId,
                new DecisionRequest(ApprovalDecision.APPROVE, null), null);
        assertThat(again.status()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("deciding a type the caller cannot approve is forbidden")
    void decisionRequiresTheTypesApprovePermission() {
        UUID transferId = pendingTransfer();
        TestSecurity.authenticateAs(Set.of("DISCOUNT_APPROVE"), Set.of(world.branchId(), otherBranchId));
        assertThatThrownBy(() -> approvalService.decide(ApprovalType.TRANSFER, transferId,
                new DecisionRequest(ApprovalDecision.APPROVE, null), null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("requesting information records a question without changing the record")
    void requestInfoLeavesStatusUntouched() {
        UUID transferId = pendingTransfer();
        TestSecurity.authenticateAs(APPROVER_PERMS, Set.of(world.branchId(), otherBranchId));

        DecisionResponse asked = approvalService.decide(ApprovalType.TRANSFER, transferId,
                new DecisionRequest(ApprovalDecision.REQUEST_INFO, "Why is this leaving the showroom?"),
                null);
        assertThat(asked.decision()).isEqualTo(ApprovalDecision.REQUEST_INFO);
        assertThat(asked.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(movementService.get(transferId).status()).isEqualTo(MovementStatus.PENDING_APPROVAL);

        List<InformationRequestResponse> questions =
                approvalService.information(ApprovalType.TRANSFER, transferId);
        assertThat(questions).hasSize(1);
        assertThat(questions.get(0).open()).isTrue();
        assertThat(questions.get(0).requestedBy()).isEqualTo("test-user");

        // The queue flags it so the approver knows they are waiting on an answer.
        ApprovalItemResponse row = approvalService.pending(null, ApprovalType.TRANSFER).stream()
                .filter(r -> r.id().equals(transferId)).findFirst().orElseThrow();
        assertThat(row.infoRequested()).isTrue();

        // The person who raises transfers answers; the flag clears.
        TestSecurity.authenticateAs(Set.of("INVENTORY_TRANSFER"), Set.of(world.branchId(), otherBranchId));
        InformationRequestResponse answered = approvalService.answer(ApprovalType.TRANSFER,
                transferId, questions.get(0).id(), "Display piece for the new branch opening");
        assertThat(answered.open()).isFalse();
        assertThat(answered.answeredBy()).isEqualTo("test-user");

        TestSecurity.authenticateAs(APPROVER_PERMS, Set.of(world.branchId(), otherBranchId));
        row = approvalService.pending(null, ApprovalType.TRANSFER).stream()
                .filter(r -> r.id().equals(transferId)).findFirst().orElseThrow();
        assertThat(row.infoRequested()).isFalse();
    }

    @Test
    @DisplayName("a discount request can be decided through the unified path")
    void decideDiscountRequestThroughUnifiedPath() {
        UUID discountId = pendingDiscountRequest();
        TestSecurity.authenticateAs(Set.of("DISCOUNT_APPROVE"), Set.of(world.branchId()));

        DecisionResponse rejected = approvalService.decide(ApprovalType.DISCOUNT, discountId,
                new DecisionRequest(ApprovalDecision.REJECT, "Margin too thin this month"), null);
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(discountRequestService.get(discountId).decisionNote())
                .isEqualTo("Margin too thin this month");
    }
}
