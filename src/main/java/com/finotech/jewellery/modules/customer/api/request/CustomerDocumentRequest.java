package com.finotech.jewellery.modules.customer.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CustomerDocumentRequest(@NotBlank @Size(max = 50) String documentType,
                                      @NotBlank @Size(max = 100) String documentNumber,
                                      @Size(max = 500) String storageKey,
                                      @Size(max = 255) String fileName,
                                      LocalDate issueDate,
                                      LocalDate expiryDate) {
}
