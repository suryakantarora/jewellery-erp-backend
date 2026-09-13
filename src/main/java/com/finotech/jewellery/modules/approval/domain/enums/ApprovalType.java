package com.finotech.jewellery.modules.approval.domain.enums;

/**
 * The kinds of record that wait for someone's sign-off. Each carries the
 * permission that lets a user decide it and the permission that lets a user
 * raise it, so the unified endpoint can scope a list to what the caller may
 * actually act on.
 */
public enum ApprovalType {
    TRANSFER("INVENTORY_TRANSFER_APPROVE", "INVENTORY_TRANSFER", "InventoryMovement"),
    PURCHASE_ORDER("PROCUREMENT_APPROVE", "PROCUREMENT_CREATE", "PurchaseOrder"),
    REQUISITION("PROCUREMENT_APPROVE", "PROCUREMENT_CREATE", "PurchaseRequisition"),
    EXCHANGE("EXCHANGE_APPROVE", "EXCHANGE_PROCESS", "ExchangeIntake"),
    STOCK_COUNT("STOCK_COUNT_APPROVE", "STOCK_COUNT_PERFORM", "StockCount"),
    GOODS_RECEIPT("PROCUREMENT_RECEIVE", "PROCUREMENT_RECEIVE", "GoodsReceipt"),
    DISCOUNT("DISCOUNT_APPROVE", "DISCOUNT_REQUEST", "DiscountRequest");

    private final String approvePermission;
    private final String createPermission;
    private final String entityType;

    ApprovalType(String approvePermission, String createPermission, String entityType) {
        this.approvePermission = approvePermission;
        this.createPermission = createPermission;
        this.entityType = entityType;
    }

    public String approvePermission() {
        return approvePermission;
    }

    public String createPermission() {
        return createPermission;
    }

    /** Entity name used in audit records and notification references. */
    public String entityType() {
        return entityType;
    }
}
