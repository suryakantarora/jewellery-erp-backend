package com.finotech.jewellery.modules.supplier.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierContactRequest(@NotBlank @Size(max = 150) String name,
                                     @Size(max = 100) String designation,
                                     @Size(max = 30) String phone,
                                     @Email @Size(max = 150) String email,
                                     boolean primaryContact) {
}
