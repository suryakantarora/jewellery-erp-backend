package com.finotech.jewellery.modules.gemstone.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record GemstoneRequest(@NotBlank @Size(max = 30) String code,
                              @NotBlank @Size(max = 100) String name,
                              boolean diamond,
                              boolean precious,
                              @Size(max = 255) String description,
                              UUID companyId) {
}
