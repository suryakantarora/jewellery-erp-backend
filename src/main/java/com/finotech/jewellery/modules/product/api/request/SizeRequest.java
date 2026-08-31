package com.finotech.jewellery.modules.product.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SizeRequest(@NotNull UUID productTypeId,
                          @NotBlank @Size(max = 30) String code,
                          @NotBlank @Size(max = 50) String label,
                          @Size(max = 30) String standard,
                          Integer displayOrder) {
}
