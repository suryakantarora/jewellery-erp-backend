package com.finotech.jewellery.modules.inventory.api.request;

import com.finotech.jewellery.modules.inventory.domain.enums.MovementType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateMovementRequest(@NotNull MovementType movementType,
                                    UUID fromLocationId,
                                    @NotNull UUID toLocationId,
                                    @NotEmpty List<UUID> itemIds,
                                    @Size(max = 500) String notes) {
}
