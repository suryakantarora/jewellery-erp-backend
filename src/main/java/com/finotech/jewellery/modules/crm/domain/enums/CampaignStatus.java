package com.finotech.jewellery.modules.crm.domain.enums;

public enum CampaignStatus {
    DRAFT,
    SCHEDULED,
    RUNNING,
    COMPLETED,
    CANCELLED;

    public boolean isEditable() {
        return this == DRAFT || this == SCHEDULED;
    }
}
