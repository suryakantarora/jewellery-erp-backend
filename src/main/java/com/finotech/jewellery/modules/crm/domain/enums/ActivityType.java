package com.finotech.jewellery.modules.crm.domain.enums;

/**
 * How the business interacted with a customer. Kept separate from the business
 * documents themselves: a sale is a sale, and the call that led to it is an
 * activity.
 */
public enum ActivityType {
    CALL,
    VISIT,
    MESSAGE,
    EMAIL,
    MEETING,
    COMPLAINT,
    FEEDBACK,
    NOTE
}
