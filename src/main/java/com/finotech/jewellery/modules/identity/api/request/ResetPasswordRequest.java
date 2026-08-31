package com.finotech.jewellery.modules.identity.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(@NotBlank @Size(min = 10, max = 100) String newPassword,
                                   boolean mustChangePassword) {
}
