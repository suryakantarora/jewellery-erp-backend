package com.finotech.jewellery.modules.organization.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record BranchRequest(@NotNull UUID companyId,
                            @NotBlank @Size(max = 30) String code,
                            @NotBlank @Size(max = 150) String name,
                            boolean headOffice,
                            @Size(max = 255) String addressLine,
                            @Size(max = 100) String city,
                            @Size(max = 100) String country,
                            @Size(max = 30) String phone,
                            @Email @Size(max = 150) String email,
                            @Size(max = 50) String timezone) {
}
