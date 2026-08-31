package com.finotech.jewellery.modules.organization.api.response;

import com.finotech.jewellery.modules.organization.domain.entity.Branch;
import com.finotech.jewellery.modules.organization.domain.enums.OrganizationStatus;
import java.util.UUID;

public record BranchResponse(UUID id, UUID companyId, String code, String name, boolean headOffice,
                             String addressLine, String city, String country, String phone,
                             String email, String timezone, OrganizationStatus status) {

    public static BranchResponse from(Branch b) {
        return new BranchResponse(b.getId(), b.getCompany().getId(), b.getCode(), b.getName(),
                b.isHeadOffice(), b.getAddressLine(), b.getCity(), b.getCountry(), b.getPhone(),
                b.getEmail(), b.getTimezone(), b.getStatus());
    }
}
