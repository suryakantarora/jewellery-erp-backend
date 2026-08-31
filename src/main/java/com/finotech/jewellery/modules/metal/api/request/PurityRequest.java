package com.finotech.jewellery.modules.metal.api.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record PurityRequest(@NotNull UUID metalId,
                            @NotBlank @Size(max = 20) String code,
                            @NotBlank @Size(max = 50) String name,
                            @NotNull @DecimalMin(value = "0.0", inclusive = false)
                            @DecimalMax("1.0") BigDecimal fineness,
                            Integer displayOrder) {
}
