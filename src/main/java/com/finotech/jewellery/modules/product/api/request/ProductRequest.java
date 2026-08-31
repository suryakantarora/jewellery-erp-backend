package com.finotech.jewellery.modules.product.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(@NotBlank @Size(max = 50) String sku,
                             @NotBlank @Size(max = 200) String name,
                             UUID designId,
                             @NotNull UUID productTypeId,
                             UUID categoryId,
                             UUID brandId,
                             UUID collectionId,
                             UUID defaultMetalId,
                             UUID defaultPurityId,
                             @DecimalMin(value = "0.0", inclusive = false) BigDecimal nominalGrossWeight,
                             @Size(max = 20) String defaultMakingChargeType,
                             @DecimalMin("0.0") BigDecimal defaultMakingChargeValue,
                             @DecimalMin("0.0") BigDecimal defaultWastagePercentage,
                             @Size(max = 30) String hsnCode,
                             String description) {
}
