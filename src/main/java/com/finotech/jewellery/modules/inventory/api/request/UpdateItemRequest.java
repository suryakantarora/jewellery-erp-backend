package com.finotech.jewellery.modules.inventory.api.request;

import com.finotech.jewellery.modules.gemstone.application.StoneRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UpdateItemRequest(@NotNull @DecimalMin(value = "0.0", inclusive = false)
                                BigDecimal grossWeight,
                                UUID sizeId,
                                @Size(max = 50) String hallmarkNumber,
                                @DecimalMin("0.0") BigDecimal purchaseCost,
                                @DecimalMin("0.0") BigDecimal makingCost,
                                @DecimalMin("0.0") BigDecimal stoneCost,
                                @Size(max = 500) String notes,
                                @Valid List<StoneRegistry.StoneSpec> stones) {
}
