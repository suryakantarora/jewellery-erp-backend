package com.finotech.jewellery.modules.finance.domain.enums;

/**
 * The five account classes, and which side of the ledger increases each.
 *
 * <p>Knowing the normal balance is what lets a report present a balance the way
 * an accountant expects: an asset with a credit balance is unusual and should
 * look unusual.
 */
public enum AccountType {

    ASSET(true),
    EXPENSE(true),
    LIABILITY(false),
    EQUITY(false),
    INCOME(false);

    private final boolean debitNormal;

    AccountType(boolean debitNormal) {
        this.debitNormal = debitNormal;
    }

    /** True when debits increase this account. */
    public boolean isDebitNormal() {
        return debitNormal;
    }

    /** Income and expense close into equity; the rest carry forward. */
    public boolean isProfitAndLoss() {
        return this == INCOME || this == EXPENSE;
    }

    public boolean isBalanceSheet() {
        return !isProfitAndLoss();
    }
}
