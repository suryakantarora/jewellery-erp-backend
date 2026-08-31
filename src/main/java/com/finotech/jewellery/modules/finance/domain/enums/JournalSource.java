package com.finotech.jewellery.modules.finance.domain.enums;

/**
 * What caused a journal entry. Every automatic posting names its source
 * document, so any figure in the ledger can be traced back to the business
 * event that produced it.
 */
public enum JournalSource {
    SALE,
    PAYMENT,
    REFUND,
    PURCHASE_INVOICE,
    EXCHANGE,
    LOYALTY,
    INVENTORY,
    /** Entered by hand; always requires a description and is audited. */
    MANUAL
}
