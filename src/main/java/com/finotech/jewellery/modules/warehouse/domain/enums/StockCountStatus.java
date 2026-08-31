package com.finotech.jewellery.modules.warehouse.domain.enums;

/**
 * Physical verification workflow. A count is only closed after its variances
 * have been reviewed, and closing a count with variances requires approval.
 */
public enum StockCountStatus {
    /** Expected stock has been captured; counting is under way. */
    IN_PROGRESS,
    /** Counting finished; variances await review. */
    PENDING_REVIEW,
    APPROVED,
    /** Approved and any adjustments applied. */
    CLOSED,
    CANCELLED
}
