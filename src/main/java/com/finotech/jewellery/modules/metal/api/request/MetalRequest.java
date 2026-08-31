package com.finotech.jewellery.modules.metal.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MetalRequest(@NotBlank @Size(max = 30) String code,
                           @NotBlank @Size(max = 100) String name,
                           @Size(max = 10) String symbol,
                           @Size(max = 10) String weightUnit,
                           @Size(max = 255) String description) {
}
