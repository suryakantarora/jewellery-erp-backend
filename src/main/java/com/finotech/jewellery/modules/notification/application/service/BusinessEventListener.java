package com.finotech.jewellery.modules.notification.application.service;

import com.finotech.jewellery.modules.notification.domain.enums.RecipientType;
import com.finotech.jewellery.shared.event.DomainEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to business events (dependency rule 7).
 *
 * <p>Every listener runs {@code AFTER_COMMIT}: a customer is never told their
 * sale completed if the transaction that recorded it was rolled back.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSaleCompleted(DomainEvents.SaleCompleted event) {
        notificationService.queueFor(event, RecipientType.CUSTOMER, event.customerId(),
                "Sale", event.saleId());
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
}
