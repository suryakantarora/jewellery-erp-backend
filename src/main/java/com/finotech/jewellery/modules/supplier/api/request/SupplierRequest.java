package com.finotech.jewellery.modules.supplier.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record SupplierRequest(@NotBlank @Size(max = 30) String code,
                              @NotBlank @Size(max = 200) String name,
                              @Size(max = 200) String legalName,
                              @Size(max = 50) String taxNumber,
                              @Size(max = 30) String supplierType,
                              @Size(max = 255) String addressLine,
                              @Size(max = 100) String city,
                              @Size(max = 100) String country,
                              @Size(max = 30) String phone,
                              @Email @Size(max = 150) String email,
                              @Size(min = 3, max = 3) String currency,
                              @PositiveOrZero Integer paymentTermsDays,
                              @DecimalMin("0.0") BigDecimal creditLimit,
                              @Size(max = 500) String notes) {
}
