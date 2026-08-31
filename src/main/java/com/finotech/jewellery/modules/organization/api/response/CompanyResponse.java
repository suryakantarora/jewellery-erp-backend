package com.finotech.jewellery.modules.organization.api.response;

import com.finotech.jewellery.modules.organization.domain.entity.Company;
import com.finotech.jewellery.modules.organization.domain.enums.OrganizationStatus;
import java.util.UUID;

public record CompanyResponse(UUID id, String code, String name, String legalName, String taxNumber,
                              String registrationNumber, String baseCurrency, String addressLine,
                              String city, String country, String phone, String email,
                              OrganizationStatus status) {

    public static CompanyResponse from(Company c) {
        return new CompanyResponse(c.getId(), c.getCode(), c.getName(), c.getLegalName(),
                c.getTaxNumber(), c.getRegistrationNumber(), c.getBaseCurrency(), c.getAddressLine(),
                c.getCity(), c.getCountry(), c.getPhone(), c.getEmail(), c.getStatus());
    }
}
