package com.finotech.jewellery.modules.organization.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyRequest(@NotBlank @Size(max = 30) String code,
                             @NotBlank @Size(max = 150) String name,
                             @Size(max = 200) String legalName,
                             @Size(max = 50) String taxNumber,
                             @Size(max = 50) String registrationNumber,
                             @Size(min = 3, max = 3) String baseCurrency,
                             @Size(max = 255) String addressLine,
                             @Size(max = 100) String city,
                             @Size(max = 100) String country,
                             @Size(max = 30) String phone,
                             @Email @Size(max = 150) String email) {
}
