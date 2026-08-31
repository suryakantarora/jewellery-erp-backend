package com.finotech.jewellery.modules.gemstone.api.response;

import com.finotech.jewellery.modules.gemstone.domain.entity.StoneCertificate;
import java.time.LocalDate;
import java.util.UUID;

public record CertificateResponse(UUID id, String certificateNumber, String issuingLab,
                                  LocalDate issueDate, String storageKey, String verificationUrl,
                                  String notes) {

    public static CertificateResponse from(StoneCertificate c) {
        return new CertificateResponse(c.getId(), c.getCertificateNumber(), c.getIssuingLab(),
                c.getIssueDate(), c.getStorageKey(), c.getVerificationUrl(), c.getNotes());
    }
}
