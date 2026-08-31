package com.finotech.jewellery.modules.inventory.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ReserveItemRequest(@NotNull UUID jewelleryItemId,
                                 @NotNull UUID customerId,
                                 @Positive Integer holdHours,
                                 @Size(max = 500) String notes) {
}
