package com.finotech.jewellery.modules.metal.api.request;

import com.finotech.jewellery.modules.metal.domain.enums.RateType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PublishRateRequest(@NotNull UUID metalId,
                                 @NotNull UUID purityId,
                                 @NotNull RateType rateType,
                                 @NotNull LocalDate effectiveDate,
                                 @NotNull @DecimalMin(value = "0.0", inclusive = false)
                                 BigDecimal ratePerUnit,
                                 @Size(min = 3, max = 3) String currency,
                                 UUID branchId,
                                 @Size(max = 255) String notes) {
}
