package com.finotech.jewellery.modules.approval.domain.enums;

public enum ApprovalDecision {
    APPROVE,
    REJECT,
    /** Ask the requester a question; the record's status does not change. */
    REQUEST_INFO;

    public boolean requiresReason() {
        return this != APPROVE;
    }
}
