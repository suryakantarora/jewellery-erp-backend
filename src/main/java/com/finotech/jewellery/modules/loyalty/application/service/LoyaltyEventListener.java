package com.finotech.jewellery.modules.loyalty.application.service;

import com.finotech.jewellery.shared.event.DomainEvents;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Awards loyalty points off completed sales (section 13).
 *
 * <p>Listening rather than being called from Sales means loyalty can be changed,
 * or switched off entirely, without touching the sale path. Points are awarded
 * after commit and in a new transaction: a loyalty failure must never roll back
 * a sale that has already been paid for.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoyaltyEventListener {

    private final LoyaltyService loyaltyService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSaleCompleted(DomainEvents.SaleCompleted event) {
        try {
            BigDecimal eligible = (BigDecimal) event.payload().get("totalAmount");
            long points = loyaltyService.awardForSale(event.customerId(), event.saleId(),
                    eligible, event.branchId());
            if (points > 0) {
                log.debug("Awarded {} loyalty point(s) for sale {}", points, event.saleId());
            }
        } catch (RuntimeException ex) {
            // The sale is already committed and paid; loyalty is a benefit, not a
            // precondition. Log it for correction rather than failing the customer.
            log.error("Could not award loyalty points for sale {}", event.saleId(), ex);
        }
    }
}
