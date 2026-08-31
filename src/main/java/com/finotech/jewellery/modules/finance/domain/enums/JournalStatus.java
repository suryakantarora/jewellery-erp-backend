package com.finotech.jewellery.modules.finance.domain.enums;

public enum JournalStatus {
    DRAFT,
    /** Posted to the ledger; immutable from here on. */
    POSTED,
    /** Cancelled by an opposite entry rather than being edited or deleted. */
    REVERSED
}
