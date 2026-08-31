package com.finotech.jewellery.modules.customer.domain.enums;

/**
 * KYC state. High-value jewellery sales are reportable in many jurisdictions,
 * so the platform tracks verification explicitly rather than assuming it.
 */
public enum KycStatus {
    NOT_REQUIRED,
    PENDING,
    VERIFIED,
    REJECTED,
    EXPIRED
}
