package com.finotech.jewellery.modules.procurement.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderRequest(@NotNull UUID supplierId,
                                   @NotNull UUID branchId,
                                   @NotNull UUID deliveryLocationId,
                                   UUID requisitionId,
                                   LocalDate expectedDeliveryDate,
                                   @Size(min = 3, max = 3) String currency,
                                   @Size(max = 500) String notes,
                                   @NotEmpty @Valid List<Line> lines) {

    public record Line(@NotNull UUID productId,
                       UUID metalId,
                       UUID purityId,
                       @Positive int orderedQuantity,
                       @DecimalMin("0.0") BigDecimal estimatedWeight,
                       @DecimalMin("0.0") BigDecimal ratePerGram,
                       @DecimalMin("0.0") BigDecimal makingChargePerUnit,
                       @Size(max = 255) String notes) {
    }
}
