package com.finotech.jewellery.modules.pricing.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TaxRateRequest(@NotBlank @Size(max = 30) String code,
                             @NotBlank @Size(max = 100) String name,
                             @NotNull @DecimalMin("0.0") BigDecimal percentage,
                             UUID branchId,
                             UUID productTypeId,
                             boolean inclusive,
                             @NotNull LocalDate effectiveFrom,
                             LocalDate effectiveTo) {
}
