package com.finotech.jewellery.modules.customer.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerAddressRequest(@Size(max = 20) String addressType,
                                     @NotBlank @Size(max = 255) String addressLine1,
                                     @Size(max = 255) String addressLine2,
                                     @Size(max = 100) String city,
                                     @Size(max = 100) String province,
                                     @Size(max = 20) String postalCode,
                                     @Size(max = 100) String country,
                                     boolean defaultAddress) {
}
