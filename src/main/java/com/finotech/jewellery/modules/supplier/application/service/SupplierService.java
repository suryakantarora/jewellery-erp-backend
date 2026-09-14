package com.finotech.jewellery.modules.supplier.application.service;

import com.finotech.jewellery.modules.supplier.api.request.SupplierBankAccountRequest;
import com.finotech.jewellery.modules.supplier.api.request.SupplierContactRequest;
import com.finotech.jewellery.modules.supplier.api.request.SupplierRequest;
import com.finotech.jewellery.modules.supplier.api.response.SupplierResponse;
import com.finotech.jewellery.modules.supplier.application.SupplierDirectory;
import com.finotech.jewellery.modules.supplier.domain.entity.Supplier;
import com.finotech.jewellery.modules.supplier.domain.entity.SupplierBankAccount;
import com.finotech.jewellery.modules.supplier.domain.entity.SupplierContact;
import com.finotech.jewellery.modules.supplier.domain.enums.SupplierStatus;
import com.finotech.jewellery.modules.supplier.infrastructure.repository.SupplierRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.finotech.jewellery.modules.organization.application.CompanyScope;

/**
 * Supplier master data with contacts, bank details and documents.
 */
@Service
@RequiredArgsConstructor
public class SupplierService implements SupplierDirectory {

    private final SupplierRepository supplierRepository;
    private final AuditService auditService;
    private final CompanyScope companyScope;

    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> search(String search, SupplierStatus status,
                                                 Pageable pageable) {
        return PageResponse.of(supplierRepository.search(companyScope.currentOrNull(), search, status, pageable),
                SupplierResponse::summary);
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(UUID id) {
        return SupplierResponse.detailed(requireWithDetails(id));
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        UUID companyId = companyScope.resolveForCreate(request.companyId());
        if (supplierRepository.existsByCompanyIdAndCodeIgnoreCase(companyId, request.code())) {
            throw new ConflictException("Supplier code already exists: " + request.code());
        }
        Supplier supplier = new Supplier();
        supplier.setCompanyId(companyId);
        apply(supplier, request);
        Supplier saved = supplierRepository.save(supplier);
        auditService.record("SUPPLIER_CREATED", "Supplier", saved.getId(), null,
                SupplierResponse.summary(saved));
        return SupplierResponse.detailed(saved);
    }

    @Transactional
    public SupplierResponse update(UUID id, SupplierRequest request) {
        Supplier supplier = requireSupplierEntity(id);
        SupplierResponse before = SupplierResponse.summary(supplier);
        apply(supplier, request);
        auditService.record("SUPPLIER_UPDATED", "Supplier", id, before,
                SupplierResponse.summary(supplier));
        return SupplierResponse.detailed(supplier);
    }

    /**
     * Blocking a supplier stops new purchase orders but leaves history intact.
     */
    @Transactional
    public SupplierResponse changeStatus(UUID id, SupplierStatus status, String reason) {
        Supplier supplier = requireSupplierEntity(id);
        SupplierStatus previous = supplier.getStatus();
        supplier.setStatus(status);
        auditService.record("SUPPLIER_STATUS_CHANGED", "Supplier", id,
                java.util.Map.of("status", previous),
                java.util.Map.of("status", status, "reason", String.valueOf(reason)));
        return SupplierResponse.detailed(supplier);
    }

    @Transactional
    public SupplierResponse addContact(UUID id, SupplierContactRequest request) {
        Supplier supplier = requireWithDetails(id);
        SupplierContact contact = new SupplierContact();
        contact.setName(request.name().trim());
        contact.setDesignation(request.designation());
        contact.setPhone(request.phone());
        contact.setEmail(request.email());
        contact.setPrimaryContact(request.primaryContact());
        if (request.primaryContact()) {
            supplier.getContacts().forEach(c -> c.setPrimaryContact(false));
        }
        supplier.addContact(contact);
        return SupplierResponse.detailed(supplier);
    }

    @Transactional
    public SupplierResponse addBankAccount(UUID id, SupplierBankAccountRequest request) {
        Supplier supplier = requireWithDetails(id);
        SupplierBankAccount account = new SupplierBankAccount();
        account.setBankName(request.bankName().trim());
        account.setAccountName(request.accountName().trim());
        account.setAccountNumber(request.accountNumber().trim());
        account.setBranchName(request.branchName());
        account.setSwiftCode(request.swiftCode());
        account.setCurrency(StringUtils.hasText(request.currency())
                ? request.currency().toUpperCase() : supplier.getCurrency());
        account.setPrimaryAccount(request.primaryAccount());
        if (request.primaryAccount()) {
            supplier.getBankAccounts().forEach(a -> a.setPrimaryAccount(false));
        }
        supplier.addBankAccount(account);

        // Bank details are payment-sensitive, so the change is always recorded.
        auditService.record("SUPPLIER_BANK_ACCOUNT_ADDED", "Supplier", id, null,
                java.util.Map.of("bank", account.getBankName(),
                        "accountNumber", masked(account.getAccountNumber())));
        return SupplierResponse.detailed(supplier);
    }

    // ---------- cross-module directory ----------

    @Override
    @Transactional(readOnly = true)
    public SupplierView requireSupplier(UUID supplierId) {
        return toView(requireSupplierEntity(supplierId));
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierView requireTradableSupplier(UUID supplierId) {
        Supplier supplier = requireSupplierEntity(supplierId);
        if (!supplier.canTrade()) {
            throw new ValidationException("Supplier " + supplier.getCode() + " is "
                    + supplier.getStatus() + " and cannot be traded with");
        }
        return toView(supplier);
    }

    // ---------- helpers ----------

    /** Another company's supplier is reported as absent, never as forbidden. */
    private Supplier requireSupplierEntity(UUID id) {
        return supplierRepository.findByIdInCompany(id, companyScope.currentOrNull())
                .orElseThrow(() -> NotFoundException.of("Supplier", id));
    }

    private Supplier requireWithDetails(UUID id) {
        Supplier supplier = supplierRepository.findWithDetailsById(id)
                .orElseThrow(() -> NotFoundException.of("Supplier", id));
        UUID scope = companyScope.currentOrNull();
        if (scope != null && !scope.equals(supplier.getCompanyId())) {
            throw NotFoundException.of("Supplier", id);
        }
        return supplier;
    }

    private SupplierView toView(Supplier s) {
        return new SupplierView(s.getId(), s.getCode(), s.getName(), s.getCurrency(),
                s.getPaymentTermsDays(), s.canTrade());
    }

    private void apply(Supplier supplier, SupplierRequest request) {
        supplier.setCode(request.code().trim().toUpperCase());
        supplier.setName(request.name().trim());
        supplier.setLegalName(request.legalName());
        supplier.setTaxNumber(request.taxNumber());
        supplier.setSupplierType(request.supplierType());
        supplier.setAddressLine(request.addressLine());
        supplier.setCity(request.city());
        supplier.setCountry(request.country());
        supplier.setPhone(request.phone());
        supplier.setEmail(request.email());
        supplier.setPaymentTermsDays(request.paymentTermsDays());
        supplier.setCreditLimit(request.creditLimit());
        supplier.setNotes(request.notes());
        if (StringUtils.hasText(request.currency())) {
            supplier.setCurrency(request.currency().toUpperCase());
        }
    }

    /** Audit trails record only the last four digits of an account number. */
    private String masked(String accountNumber) {
        int visible = 4;
        return accountNumber.length() <= visible
                ? "****"
                : "****" + accountNumber.substring(accountNumber.length() - visible);
    }
}
