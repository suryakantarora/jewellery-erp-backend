package com.finotech.jewellery.modules.finance.application.service;

import com.finotech.jewellery.modules.finance.domain.enums.JournalSource;
import com.finotech.jewellery.modules.finance.domain.enums.StandardAccount;
import com.finotech.jewellery.modules.inventory.application.InventoryOperations;
import com.finotech.jewellery.modules.sales.api.response.SaleResponse;
import com.finotech.jewellery.modules.sales.application.service.SaleService;
import com.finotech.jewellery.shared.event.DomainEvents;
import com.finotech.jewellery.shared.utils.MoneyUtils;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns business events into ledger entries (dependency rule 6: finance consumes
 * finalised business events).
 *
 * <p>Listening rather than being called means no business module knows finance
 * exists, and accounting policy can change without touching a sale. Postings run
 * after commit in their own transaction: a bookkeeping problem must never roll
 * back a sale the customer has already paid for. When a posting does fail it is
 * logged loudly, because an unposted sale is a real gap that needs correcting,
 * not something to swallow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinancePostingListener {

    private static final String PARTY_CUSTOMER = "CUSTOMER";

    private final JournalPostingService posting;
    private final SaleService saleService;
    private final InventoryOperations inventory;

    /**
     * A confirmed sale, in two entries: the revenue side, and cost of goods sold.
     *
     * <pre>
     *   DR Customer Deposits     amount already paid before confirmation
     *   DR Loyalty Liability     value of points redeemed
     *   DR Scrap Metal           value of jewellery taken in exchange
     *   DR Accounts Receivable   whatever is still owed
     *     CR Sales Revenue       sub total less discounts
     *     CR Tax Payable         tax charged
     * </pre>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSaleCompleted(DomainEvents.SaleCompleted event) {
        try {
            SaleResponse sale = saleService.get(event.saleId());

            BigDecimal revenue = sale.subTotal().subtract(sale.discountTotal());
            BigDecimal receivable = sale.outstandingAmount().max(BigDecimal.ZERO);

            posting.post(JournalSource.SALE, "Sale", sale.id(), sale.saleDate(),
                    "Sale " + sale.saleNumber() + " invoice " + sale.invoiceNumber(),
                    sale.branchId(),
                    JournalPostingService.entry()
                            .debit(StandardAccount.CUSTOMER_DEPOSITS, sale.paidAmount(),
                                    "Applied against the invoice", PARTY_CUSTOMER,
                                    sale.customerId())
                            .debit(StandardAccount.LOYALTY_LIABILITY,
                                    sale.loyaltyRedemptionValue(), "Points redeemed",
                                    PARTY_CUSTOMER, sale.customerId())
                            .debit(StandardAccount.SCRAP_METAL, sale.exchangeCredit(),
                                    "Old jewellery taken in exchange")
                            .debit(StandardAccount.ACCOUNTS_RECEIVABLE, receivable,
                                    "Outstanding on " + sale.saleNumber(), PARTY_CUSTOMER,
                                    sale.customerId())
                            .credit(StandardAccount.SALES_REVENUE, revenue,
                                    "Sale " + sale.saleNumber())
                            .credit(StandardAccount.TAX_PAYABLE, sale.taxTotal(),
                                    "Tax on " + sale.saleNumber()));

            postCostOfGoodsSold(sale);
        } catch (RuntimeException ex) {
            log.error("Could not post sale {} to the ledger; it needs a manual entry",
                    event.saleId(), ex);
        }
    }

    /**
     * Money in.
     *
     * <p>Credited to customer deposits rather than receivables, because a
     * payment can arrive before the sale is confirmed. The confirmation entry
     * then clears the deposit. Doing it the other way round would leave
     * receivables negative between the two.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentReceived(DomainEvents.PaymentReceived event) {
        try {
            BigDecimal amount = (BigDecimal) event.payload().get("amount");
            String method = String.valueOf(event.payload().get("method"));
            UUID paymentId = (UUID) event.payload().get("paymentId");

            posting.post(JournalSource.PAYMENT, "Payment", paymentId, null,
                    "Payment received by " + method, event.branchId(),
                    JournalPostingService.entry()
                            .debit(cashAccountFor(method), amount, "Received by " + method)
                            .credit(StandardAccount.CUSTOMER_DEPOSITS, amount,
                                    "Held against the sale", PARTY_CUSTOMER, event.customerId()));
        } catch (RuntimeException ex) {
            log.error("Could not post payment for sale {} to the ledger", event.saleId(), ex);
        }
    }

    /**
     * Buying old jewellery: metal comes in, money goes out.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onExchangeCompleted(DomainEvents.ExchangeCompleted event) {
        try {
            BigDecimal valuation = (BigDecimal) event.payload().get("valuation");
            UUID exchangeId = (UUID) event.payload().get("exchangeId");
            String reference = String.valueOf(event.payload().get("referenceNumber"));

            posting.post(JournalSource.EXCHANGE, "ExchangeIntake", exchangeId, null,
                    "Old jewellery taken in on " + reference, event.branchId(),
                    JournalPostingService.entry()
                            .debit(StandardAccount.SCRAP_METAL, valuation,
                                    "Metal received on " + reference)
                            .credit(StandardAccount.CASH, valuation,
                                    "Paid out on " + reference, PARTY_CUSTOMER,
                                    event.customerId()));
        } catch (RuntimeException ex) {
            log.error("Could not post exchange {} to the ledger",
                    event.payload().get("exchangeId"), ex);
        }
    }

    /**
     * Points awarded are a promise to give value later, so they are recognised
     * as a liability when earned rather than when spent.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLoyaltyPointsEarned(DomainEvents.LoyaltyPointsEarned event) {
        try {
            BigDecimal value = (BigDecimal) event.payload().get("value");
            posting.post(JournalSource.LOYALTY, "LoyaltyAward", event.payload().get("saleId"),
                    null, "Loyalty points earned", event.branchId(),
                    JournalPostingService.entry()
                            .debit(StandardAccount.LOYALTY_EXPENSE, value, "Points awarded")
                            .credit(StandardAccount.LOYALTY_LIABILITY, value,
                                    "Points owed to the customer", PARTY_CUSTOMER,
                                    event.customerId()));
        } catch (RuntimeException ex) {
            log.error("Could not post loyalty award for sale {}",
                    event.payload().get("saleId"), ex);
        }
    }

    /**
     * Stock arriving from a supplier.
     *
     * <pre>
     *   DR Jewellery Inventory   what the goods cost
     *     CR Accounts Payable    what is owed to the supplier
     * </pre>
     *
     * <p>Without this the inventory account would only ever be credited, and the
     * balance sheet would show stock as a negative asset.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onGoodsReceived(DomainEvents.GoodsReceived event) {
        try {
            BigDecimal totalCost = (BigDecimal) event.payload().get("totalCost");
            String reference = String.valueOf(event.payload().get("receiptNumber"));

            posting.post(JournalSource.PURCHASE_INVOICE, "GoodsReceipt", event.goodsReceiptId(),
                    null, "Stock received on " + reference, event.branchId(),
                    JournalPostingService.entry()
                            .debit(StandardAccount.INVENTORY, totalCost,
                                    "Stock received on " + reference)
                            .credit(StandardAccount.ACCOUNTS_PAYABLE, totalCost,
                                    "Owed on " + reference, "SUPPLIER", event.supplierId()));
        } catch (RuntimeException ex) {
            log.error("Could not post goods receipt {} to the ledger",
                    event.goodsReceiptId(), ex);
        }
    }

    /**
     * Cost of goods sold, taken from what each item actually cost.
     *
     * <p>Posted as its own entry so revenue and cost can be reversed
     * independently — a returned item changes cost without changing the invoice.
     */
    private void postCostOfGoodsSold(SaleResponse sale) {
        BigDecimal totalCost = BigDecimal.ZERO;
        for (SaleResponse.SaleLineResponse line : sale.lines()) {
            InventoryOperations.ItemView item = inventory.requireItem(line.jewelleryItemId());
            totalCost = totalCost.add(MoneyUtils.nullSafe(item.totalCost()));
        }
        if (totalCost.signum() <= 0) {
            // Items received without a recorded cost produce no COGS entry rather
            // than a guessed one.
            return;
        }
        posting.post(JournalSource.INVENTORY, "SaleCogs", sale.id(), sale.saleDate(),
                "Cost of goods sold on " + sale.saleNumber(), sale.branchId(),
                JournalPostingService.entry()
                        .debit(StandardAccount.COST_OF_GOODS_SOLD, totalCost,
                                "Cost of items on " + sale.saleNumber())
                        .credit(StandardAccount.INVENTORY, totalCost,
                                "Stock released on " + sale.saleNumber()));
    }

    /** Card takings sit in a clearing account until the acquirer settles. */
    private StandardAccount cashAccountFor(String method) {
        return switch (method) {
            case "CASH" -> StandardAccount.CASH;
            case "CARD", "QR_PAYMENT" -> StandardAccount.CARD_CLEARING;
            case "BANK_TRANSFER" -> StandardAccount.BANK;
            case "GIFT_VOUCHER" -> StandardAccount.GIFT_VOUCHER_LIABILITY;
            case "STORE_CREDIT", "EXCHANGE_CREDIT" -> StandardAccount.CUSTOMER_DEPOSITS;
            default -> StandardAccount.CASH;
        };
    }
}
