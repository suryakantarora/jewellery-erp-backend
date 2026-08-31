package com.finotech.jewellery.modules.finance.domain.enums;

/**
 * The accounts the platform posts to automatically.
 *
 * <p>Held as an enum because the posting rules reference them by name: if an
 * account is renamed or renumbered in the chart, the rules keep working, and if
 * one is missing the failure is immediate and obvious rather than a silently
 * unbalanced ledger.
 */
public enum StandardAccount {

    CASH("1010"),
    BANK("1020"),
    CARD_CLEARING("1030"),
    ACCOUNTS_RECEIVABLE("1100"),
    INVENTORY("1200"),
    SCRAP_METAL("1210"),

    ACCOUNTS_PAYABLE("2010"),
    TAX_PAYABLE("2020"),
    CUSTOMER_DEPOSITS("2100"),
    LOYALTY_LIABILITY("2110"),
    GIFT_VOUCHER_LIABILITY("2120"),

    SALES_REVENUE("4010"),
    COST_OF_GOODS_SOLD("5010"),
    LOYALTY_EXPENSE("5200"),
    /** Difference found by a stock count, written off or on. */
    INVENTORY_ADJUSTMENT("5300");

    private final String code;

    StandardAccount(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
