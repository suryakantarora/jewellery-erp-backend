package com.finotech.jewellery.modules.warehouse.api.request;

import com.finotech.jewellery.modules.warehouse.domain.enums.BinType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class WarehouseRequests {

    private WarehouseRequests() {
    }

    public record BinRequest(@NotNull UUID locationId,
                             UUID parentId,
                             @NotBlank @Size(max = 40) String code,
                             @NotBlank @Size(max = 150) String name,
                             @NotNull BinType binType,
                             @Positive Integer capacity,
                             @Size(max = 255) String description) {
    }

    public record StartCountRequest(@NotNull UUID locationId,
                                    @Size(max = 1000) String notes) {
    }

    /**
     * The items physically found. Anything expected but absent from this list is
     * recorded as missing.
     */
    public record SubmitCountRequest(@NotNull List<UUID> foundItemIds,
                                     @Size(max = 1000) String notes) {
    }
}
