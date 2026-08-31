package com.finotech.jewellery.modules.product.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Shared shape for the flat code/name masters (brand, collection). */
public record SimpleMasterRequest(@NotBlank @Size(max = 40) String code,
                                  @NotBlank @Size(max = 150) String name,
                                  @Size(max = 255) String description) {
}
