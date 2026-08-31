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

public record RequisitionRequest(@NotNull UUID branchId,
                                 LocalDate requiredBy,
                                 @Size(max = 500) String justification,
                                 @NotEmpty @Valid List<Line> lines) {

    public record Line(@NotNull UUID productId,
                       @Positive int quantity,
                       @DecimalMin("0.0") BigDecimal estimatedWeight,
                       @Size(max = 255) String notes) {
    }
}
