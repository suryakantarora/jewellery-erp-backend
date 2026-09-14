package com.finotech.jewellery.modules.customer.api.request;

import com.finotech.jewellery.modules.customer.domain.enums.CustomerType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerRequest(@Size(max = 30) String customerCode,
                              CustomerType customerType,
                              @NotBlank @Size(max = 200) String fullName,
                              @Size(max = 200) String companyName,
                              @NotBlank @Size(max = 30) String phone,
                              @Size(max = 30) String alternatePhone,
                              @Email @Size(max = 150) String email,
                              @Past LocalDate dateOfBirth,
                              LocalDate anniversaryDate,
                              @Size(max = 20) String gender,
                              @Size(max = 50) String taxNumber,
                              UUID registeredBranchId,
                              @Size(max = 500) String notes,
                              UUID companyId) {
}
