package com.finotech.jewellery.modules.product.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CategoryRequest(@NotBlank @Size(max = 40) String code,
                              @NotBlank @Size(max = 150) String name,
                              UUID parentId,
                              @Size(max = 255) String description,
                              Integer displayOrder,
                              UUID companyId) {
}
