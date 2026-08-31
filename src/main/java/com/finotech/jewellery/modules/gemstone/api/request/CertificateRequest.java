package com.finotech.jewellery.modules.gemstone.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CertificateRequest(@NotBlank @Size(max = 100) String certificateNumber,
                                 @NotBlank @Size(max = 100) String issuingLab,
                                 LocalDate issueDate,
                                 @Size(max = 500) String storageKey,
                                 @Size(max = 500) String verificationUrl,
                                 @Size(max = 255) String notes) {
}
