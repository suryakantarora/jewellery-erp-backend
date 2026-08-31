package com.finotech.jewellery.modules.customer.api.response;

import com.finotech.jewellery.modules.customer.domain.entity.Customer;
import com.finotech.jewellery.modules.customer.domain.enums.CustomerStatus;
import com.finotech.jewellery.modules.customer.domain.enums.CustomerType;
import com.finotech.jewellery.modules.customer.domain.enums.KycStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public record CustomerResponse(UUID id, String customerCode, CustomerType customerType,
                               String fullName, String companyName, String phone,
                               String alternatePhone, String email, LocalDate dateOfBirth,
                               LocalDate anniversaryDate, String gender, String taxNumber,
                               UUID registeredBranchId, KycStatus kycStatus,
                               LocalDate kycVerifiedAt, CustomerStatus status, String notes,
                               List<AddressResponse> addresses, List<DocumentResponse> documents,
                               Map<String, String> preferences) {

    public record AddressResponse(UUID id, String addressType, String addressLine1,
                                  String addressLine2, String city, String province,
                                  String postalCode, String country, boolean defaultAddress) {
    }

    public record DocumentResponse(UUID id, String documentType, String documentNumber,
                                   String storageKey, LocalDate issueDate, LocalDate expiryDate,
                                   boolean verified, boolean expired) {
    }

    public static CustomerResponse summary(Customer c) {
        return build(c, null, null, null);
    }

    public static CustomerResponse detailed(Customer c) {
        List<AddressResponse> addresses = c.getAddresses().stream()
                .map(a -> new AddressResponse(a.getId(), a.getAddressType(), a.getAddressLine1(),
                        a.getAddressLine2(), a.getCity(), a.getProvince(), a.getPostalCode(),
                        a.getCountry(), a.isDefaultAddress()))
                .toList();
        List<DocumentResponse> documents = c.getDocuments().stream()
                .map(d -> new DocumentResponse(d.getId(), d.getDocumentType(), d.getDocumentNumber(),
                        d.getStorageKey(), d.getIssueDate(), d.getExpiryDate(), d.isVerified(),
                        d.isExpired()))
                .toList();
        Map<String, String> preferences = c.getPreferences().stream()
                .collect(Collectors.toMap(p -> p.getPreferenceKey(),
                        p -> p.getPreferenceValue() == null ? "" : p.getPreferenceValue()));
        return build(c, addresses, documents, preferences);
    }

    private static CustomerResponse build(Customer c, List<AddressResponse> addresses,
                                          List<DocumentResponse> documents,
                                          Map<String, String> preferences) {
        return new CustomerResponse(c.getId(), c.getCustomerCode(), c.getCustomerType(),
                c.getFullName(), c.getCompanyName(), c.getPhone(), c.getAlternatePhone(),
                c.getEmail(), c.getDateOfBirth(), c.getAnniversaryDate(), c.getGender(),
                c.getTaxNumber(), c.getRegisteredBranchId(), c.getKycStatus(), c.getKycVerifiedAt(),
                c.getStatus(), c.getNotes(), addresses, documents, preferences);
    }
}
