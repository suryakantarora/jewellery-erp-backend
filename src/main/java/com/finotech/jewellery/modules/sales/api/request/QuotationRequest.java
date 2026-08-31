package com.finotech.jewellery.modules.sales.api.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record QuotationRequest(@NotNull UUID customerId,
                               @NotNull UUID branchId,
                               @Positive Integer validForDays,
                               @Size(max = 500) String notes,
                               @NotEmpty List<UUID> jewelleryItemIds) {
}
