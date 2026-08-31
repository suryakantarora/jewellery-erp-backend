package com.finotech.jewellery.modules.inventory.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectMovementRequest(@NotBlank @Size(max = 500) String reason) {
}
