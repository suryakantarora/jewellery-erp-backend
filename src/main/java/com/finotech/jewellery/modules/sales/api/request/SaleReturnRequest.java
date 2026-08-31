package com.finotech.jewellery.modules.sales.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record SaleReturnRequest(@NotEmpty List<UUID> jewelleryItemIds,
                                @NotNull UUID returnToLocationId,
                                @NotBlank @Size(max = 500) String reason) {
}
