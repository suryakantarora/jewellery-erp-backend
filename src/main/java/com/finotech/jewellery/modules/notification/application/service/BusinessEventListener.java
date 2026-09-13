package com.finotech.jewellery.modules.notification.application.service;

import com.finotech.jewellery.modules.identity.application.UserDirectory;
import com.finotech.jewellery.modules.notification.domain.enums.RecipientType;
import com.finotech.jewellery.shared.event.DomainEvent;
import com.finotech.jewellery.shared.event.DomainEvents;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to business events (dependency rule 7).
 *
 * <p>Every listener runs {@code AFTER_COMMIT}: a customer is never told their
 * sale completed if the transaction that recorded it was rolled back.
 *
 * <p>Staff-directed events resolve their recipients here, through the identity
 * port, so the modules that raise them only know usernames and branches. When
 * nobody can be resolved the message falls back to a branch broadcast rather
 * than vanishing: a transfer nobody is told about is worse than one the whole
 * branch sees.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessEventListener {

    static final String TRANSFER_APPROVE = "INVENTORY_TRANSFER_APPROVE";
    static final String COMPLIANCE_REPORT = "COMPLIANCE_REPORT";
    static final String REPORT_VIEW = "REPORT_VIEW";

    private final NotificationService notificationService;
    private final UserDirectory userDirectory;

    /** Mirrors the compliance threshold; sales does not know it is one. */
    @Value("${jewellery.compliance.high-value-sale-amount:100000000}")
    private BigDecimal highValueSaleAmount;

    /** The sale event carries no currency; the platform trades in one. */
    @Value("${jewellery.notification.currency:LAK}")
    private String currency;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSaleCompleted(DomainEvents.SaleCompleted event) {
        notificationService.queueFor(event, RecipientType.CUSTOMER, event.customerId(),
                "Sale", event.saleId());

        BigDecimal total = (BigDecimal) event.payload().get("totalAmount");
        if (total != null && highValueSaleAmount != null
                && total.compareTo(highValueSaleAmount) >= 0) {
            Set<UUID> managers = new LinkedHashSet<>();
            managers.addAll(userDirectory.activeUserIdsWithPermissionInBranches(
                    COMPLIANCE_REPORT, List.of(event.branchId())));
            managers.addAll(userDirectory.activeUserIdsWithPermissionInBranches(
                    REPORT_VIEW, List.of(event.branchId())));
            DomainEvents.HighValueSale alert = new DomainEvents.HighValueSale(event.saleId(),
                    event.branchId(), (String) event.payload().get("invoiceNumber"), total,
                    currency);
            toUsersOrBranch(alert, managers, "Sale", event.saleId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentReceived(DomainEvents.PaymentReceived event) {
        notificationService.queueFor(event, RecipientType.CUSTOMER, event.customerId(),
                "Payment", event.payload().get("paymentId"));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRepairReady(DomainEvents.RepairReady event) {
        notificationService.queueFor(event, RecipientType.CUSTOMER, event.customerId(),
                "RepairRequest", event.payload().get("repairRequestId"));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExchangeCompleted(DomainEvents.ExchangeCompleted event) {
        notificationService.queueFor(event, RecipientType.CUSTOMER, event.customerId(),
                "Exchange", event.payload().get("exchangeId"));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCampaignTargeted(DomainEvents.CampaignTargeted event) {
        notificationService.queueFor(event, RecipientType.CUSTOMER, event.customerId(),
                "Campaign", event.campaignId());
    }

    /** Operational events go to staff, not customers. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onItemTransferred(DomainEvents.ItemTransferred event) {
        notificationService.queueFor(event, RecipientType.USER, null,
                "InventoryMovement", event.payload().get("movementId"));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLowStock(DomainEvents.LowStock event) {
        notificationService.queueFor(event, RecipientType.USER, null,
                "Location", event.payload().get("locationId"));
    }

    // ---------- staff-directed ----------

    /**
     * Everyone who may approve, in either branch involved. On the second
     * signature of a dual-authorisation movement the first approver is left
     * out: they cannot sign twice, so there is nothing for them to do.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransferAwaitingApproval(DomainEvents.TransferAwaitingApproval event) {
        List<UUID> branches = new ArrayList<>();
        if (event.fromBranchId() != null) {
            branches.add(event.fromBranchId());
        }
        if (event.toBranchId() != null) {
            branches.add(event.toBranchId());
        }
        Set<UUID> approvers = new LinkedHashSet<>(
                userDirectory.activeUserIdsWithPermissionInBranches(TRANSFER_APPROVE, branches));
        if (event.requiresSecondApproval()) {
            userDirectory.userIdForUsername(event.lastApprover()).ifPresent(approvers::remove);
        }
        toUsersOrBranch(event, approvers, "InventoryMovement", event.movementId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransferDecided(DomainEvents.TransferDecided event) {
        toUserOrBranch(event, userDirectory.userIdForUsername(event.createdBy()),
                "InventoryMovement", event.movementId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPurchaseOrderDecided(DomainEvents.PurchaseOrderDecided event) {
        toUserOrBranch(event, userDirectory.userIdForUsername(event.createdBy()),
                "PurchaseOrder", event.purchaseOrderId());
    }

    /** The technician who did the work and the person who logged the job. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRepairReadyStaff(DomainEvents.RepairReadyStaff event) {
        Set<UUID> recipients = new LinkedHashSet<>();
        userDirectory.userIdForUsername(event.assignedTo()).ifPresent(recipients::add);
        userDirectory.userIdForUsername(event.createdBy()).ifPresent(recipients::add);
        toUsersOrBranch(event, recipients, "RepairRequest", event.repairRequestId());
    }

    private void toUserOrBranch(DomainEvent event, Optional<UUID> userId, String referenceType,
                                Object referenceId) {
        toUsersOrBranch(event, userId.map(Set::of).orElse(Set.of()), referenceType, referenceId);
    }

    private void toUsersOrBranch(DomainEvent event, Set<UUID> userIds, String referenceType,
                                 Object referenceId) {
        if (userIds.isEmpty()) {
            log.debug("No staff recipient resolved for {}; broadcasting to branch {}",
                    event.eventType(), event.branchId());
            notificationService.queueFor(event, RecipientType.USER, null, referenceType,
                    referenceId);
            return;
        }
        notificationService.queueForUsers(event, userIds, referenceType, referenceId);
    }
}
