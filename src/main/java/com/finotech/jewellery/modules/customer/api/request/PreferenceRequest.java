package com.finotech.jewellery.modules.customer.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PreferenceRequest(@NotBlank @Size(max = 50) String key,
                                @Size(max = 255) String value) {
}
