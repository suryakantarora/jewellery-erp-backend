package com.finotech.jewellery.shared.security;

/**
 * Canonical permission names. Kept as constants so {@code @PreAuthorize}
 * expressions cannot drift from the seeded permission catalogue.
 */
public final class Permissions {

    private Permissions() {
    }

    public static final String USER_VIEW = "USER_VIEW";
    public static final String USER_MANAGE = "USER_MANAGE";
    public static final String ROLE_VIEW = "ROLE_VIEW";
    public static final String ROLE_MANAGE = "ROLE_MANAGE";

    public static final String ORGANIZATION_VIEW = "ORGANIZATION_VIEW";
    public static final String ORGANIZATION_MANAGE = "ORGANIZATION_MANAGE";

    public static final String PRODUCT_VIEW = "PRODUCT_VIEW";
    public static final String PRODUCT_CREATE = "PRODUCT_CREATE";
    public static final String PRODUCT_UPDATE = "PRODUCT_UPDATE";

    public static final String METAL_VIEW = "METAL_VIEW";
    public static final String METAL_MANAGE = "METAL_MANAGE";
    public static final String METAL_RATE_PUBLISH = "METAL_RATE_PUBLISH";

    public static final String GEMSTONE_VIEW = "GEMSTONE_VIEW";
    public static final String GEMSTONE_MANAGE = "GEMSTONE_MANAGE";

    public static final String INVENTORY_VIEW = "INVENTORY_VIEW";
    public static final String INVENTORY_CREATE = "INVENTORY_CREATE";
    public static final String INVENTORY_TRANSFER = "INVENTORY_TRANSFER";
    public static final String INVENTORY_TRANSFER_APPROVE = "INVENTORY_TRANSFER_APPROVE";
    public static final String INVENTORY_RESERVE = "INVENTORY_RESERVE";
    public static final String INVENTORY_ADJUST = "INVENTORY_ADJUST";

    public static final String PRICE_CHANGE = "PRICE_CHANGE";
    public static final String SALE_CREATE = "SALE_CREATE";
    public static final String DISCOUNT_REQUEST = "DISCOUNT_REQUEST";
    public static final String DISCOUNT_APPROVE = "DISCOUNT_APPROVE";

    public static final String SUPPLIER_VIEW = "SUPPLIER_VIEW";
    public static final String SUPPLIER_MANAGE = "SUPPLIER_MANAGE";

    public static final String CUSTOMER_VIEW = "CUSTOMER_VIEW";
    public static final String CUSTOMER_MANAGE = "CUSTOMER_MANAGE";
    public static final String CUSTOMER_KYC_VERIFY = "CUSTOMER_KYC_VERIFY";

    public static final String PROCUREMENT_VIEW = "PROCUREMENT_VIEW";
    public static final String PROCUREMENT_CREATE = "PROCUREMENT_CREATE";
    public static final String PROCUREMENT_APPROVE = "PROCUREMENT_APPROVE";
    public static final String PROCUREMENT_RECEIVE = "PROCUREMENT_RECEIVE";

    public static final String SALE_VIEW = "SALE_VIEW";
    public static final String SALE_RETURN = "SALE_RETURN";

    public static final String PAYMENT_VIEW = "PAYMENT_VIEW";
    public static final String PAYMENT_COLLECT = "PAYMENT_COLLECT";
    public static final String PAYMENT_REFUND = "PAYMENT_REFUND";
    public static final String PAYMENT_RECONCILE = "PAYMENT_RECONCILE";

    public static final String EXCHANGE_VIEW = "EXCHANGE_VIEW";
    public static final String EXCHANGE_PROCESS = "EXCHANGE_PROCESS";
    public static final String EXCHANGE_VALUE = "EXCHANGE_VALUE";
    public static final String EXCHANGE_APPROVE = "EXCHANGE_APPROVE";

    public static final String REPAIR_VIEW = "REPAIR_VIEW";
    public static final String REPAIR_PROCESS = "REPAIR_PROCESS";
    public static final String REPAIR_ESTIMATE = "REPAIR_ESTIMATE";

    public static final String WAREHOUSE_VIEW = "WAREHOUSE_VIEW";
    public static final String WAREHOUSE_MANAGE = "WAREHOUSE_MANAGE";
    public static final String STOCK_COUNT_PERFORM = "STOCK_COUNT_PERFORM";
    public static final String STOCK_COUNT_APPROVE = "STOCK_COUNT_APPROVE";

    public static final String NOTIFICATION_VIEW = "NOTIFICATION_VIEW";
    public static final String NOTIFICATION_MANAGE = "NOTIFICATION_MANAGE";

    public static final String CRM_VIEW = "CRM_VIEW";
    public static final String CRM_MANAGE = "CRM_MANAGE";
    public static final String CAMPAIGN_MANAGE = "CAMPAIGN_MANAGE";

    public static final String LOYALTY_VIEW = "LOYALTY_VIEW";
    public static final String LOYALTY_MANAGE = "LOYALTY_MANAGE";
    public static final String LOYALTY_REDEEM = "LOYALTY_REDEEM";
    public static final String LOYALTY_ADJUST = "LOYALTY_ADJUST";

    public static final String AUDIT_VIEW = "AUDIT_VIEW";
    public static final String FILE_UPLOAD = "FILE_UPLOAD";
    public static final String FILE_DOWNLOAD = "FILE_DOWNLOAD";

    public static final String FINANCE_VIEW = "FINANCE_VIEW";
    public static final String FINANCE_MANAGE = "FINANCE_MANAGE";
    public static final String FINANCE_POST = "FINANCE_POST";

    public static final String REPORT_VIEW = "REPORT_VIEW";
    public static final String COMPLIANCE_REPORT = "COMPLIANCE_REPORT";

    /** Convenience for {@code @PreAuthorize("hasAuthority(...)")} expressions. */
    public static final String HAS = "hasAuthority";
    public static final String STOREFRONT_VIEW = "STOREFRONT_VIEW";
    public static final String STOREFRONT_MANAGE = "STOREFRONT_MANAGE";
}
