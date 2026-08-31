package com.finotech.jewellery.modules.supplier.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierBankAccountRequest(@NotBlank @Size(max = 150) String bankName,
                                         @NotBlank @Size(max = 150) String accountName,
                                         @NotBlank @Size(max = 50) String accountNumber,
                                         @Size(max = 150) String branchName,
                                         @Size(max = 20) String swiftCode,
                                         @Size(min = 3, max = 3) String currency,
                                         boolean primaryAccount) {
}
