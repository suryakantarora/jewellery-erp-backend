package com.finotech.jewellery.modules.supplier.api.response;

import com.finotech.jewellery.modules.supplier.domain.entity.Supplier;
import com.finotech.jewellery.modules.supplier.domain.enums.SupplierStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SupplierResponse(UUID id, String code, String name, String legalName, String taxNumber,
                               String supplierType, String addressLine, String city, String country,
                               String phone, String email, String currency, Integer paymentTermsDays,
                               BigDecimal creditLimit, SupplierStatus status, String notes,
                               List<ContactResponse> contacts, List<BankAccountResponse> bankAccounts) {

    public record ContactResponse(UUID id, String name, String designation, String phone,
                                  String email, boolean primaryContact) {
    }

    public record BankAccountResponse(UUID id, String bankName, String accountName,
                                      String accountNumber, String branchName, String swiftCode,
                                      String currency, boolean primaryAccount) {
    }

    /** Summary view without the child collections, for list endpoints. */
    public static SupplierResponse summary(Supplier s) {
        return new SupplierResponse(s.getId(), s.getCode(), s.getName(), s.getLegalName(),
                s.getTaxNumber(), s.getSupplierType(), s.getAddressLine(), s.getCity(),
                s.getCountry(), s.getPhone(), s.getEmail(), s.getCurrency(),
                s.getPaymentTermsDays(), s.getCreditLimit(), s.getStatus(), s.getNotes(),
                null, null);
    }

    public static SupplierResponse detailed(Supplier s) {
        List<ContactResponse> contacts = s.getContacts().stream()
                .map(c -> new ContactResponse(c.getId(), c.getName(), c.getDesignation(),
                        c.getPhone(), c.getEmail(), c.isPrimaryContact()))
                .toList();
        List<BankAccountResponse> accounts = s.getBankAccounts().stream()
                .map(a -> new BankAccountResponse(a.getId(), a.getBankName(), a.getAccountName(),
                        a.getAccountNumber(), a.getBranchName(), a.getSwiftCode(), a.getCurrency(),
                        a.isPrimaryAccount()))
                .toList();
        return new SupplierResponse(s.getId(), s.getCode(), s.getName(), s.getLegalName(),
                s.getTaxNumber(), s.getSupplierType(), s.getAddressLine(), s.getCity(),
                s.getCountry(), s.getPhone(), s.getEmail(), s.getCurrency(),
                s.getPaymentTermsDays(), s.getCreditLimit(), s.getStatus(), s.getNotes(),
                contacts, accounts);
    }
}
