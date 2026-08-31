package com.finotech.jewellery.modules.inventory.api.request;

import com.finotech.jewellery.modules.inventory.domain.enums.ItemStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Manual status correction; requires INVENTORY_ADJUST and is always audited. */
public record ChangeStatusRequest(@NotNull ItemStatus targetStatus,
                                  @NotBlank @Size(max = 500) String reason) {
}
