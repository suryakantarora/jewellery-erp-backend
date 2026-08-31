package com.finotech.jewellery.modules.product.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record DesignRequest(@NotBlank @Size(max = 50) String designCode,
                            @NotBlank @Size(max = 150) String name,
                            UUID productTypeId,
                            UUID collectionId,
                            UUID brandId,
                            @Size(max = 150) String designer,
                            @DecimalMin(value = "0.0", inclusive = false) BigDecimal nominalGrossWeight,
                            String description) {
}
