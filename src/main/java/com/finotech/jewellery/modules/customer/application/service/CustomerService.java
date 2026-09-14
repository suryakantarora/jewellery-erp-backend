package com.finotech.jewellery.modules.customer.application.service;

import com.finotech.jewellery.modules.customer.api.request.CustomerAddressRequest;
import com.finotech.jewellery.modules.customer.api.request.CustomerDocumentRequest;
import com.finotech.jewellery.modules.customer.api.request.CustomerRequest;
import com.finotech.jewellery.modules.customer.api.request.PreferenceRequest;
import com.finotech.jewellery.modules.customer.api.response.CustomerResponse;
import com.finotech.jewellery.modules.customer.application.CustomerDirectory;
import com.finotech.jewellery.modules.customer.domain.entity.Customer;
import com.finotech.jewellery.modules.customer.domain.entity.CustomerAddress;
import com.finotech.jewellery.modules.customer.domain.entity.CustomerDocument;
import com.finotech.jewellery.modules.customer.domain.entity.CustomerPreference;
import com.finotech.jewellery.modules.customer.domain.enums.CustomerStatus;
import com.finotech.jewellery.modules.customer.domain.enums.CustomerType;
import com.finotech.jewellery.modules.customer.domain.enums.KycStatus;
import com.finotech.jewellery.modules.customer.infrastructure.repository.CustomerRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.security.SecurityUtils;
import com.finotech.jewellery.shared.utils.CodeGenerator;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.finotech.jewellery.modules.organization.application.CompanyScope;

/**
 * Customer master data, addresses, documents, preferences and KYC state.
 */
@Service
@RequiredArgsConstructor
public class CustomerService implements CustomerDirectory {

    private final CustomerRepository customerRepository;
    private final AuditService auditService;
    private final CompanyScope companyScope;

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> search(String search, CustomerStatus status,
                                                 KycStatus kycStatus, UUID branchId,
                                                 Pageable pageable) {
        return PageResponse.of(customerRepository.search(companyScope.currentOrNull(), search, status,
                        kycStatus, branchId, pageable),
                CustomerResponse::summary);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(UUID id) {
        return CustomerResponse.detailed(requireWithDetails(id));
    }

    /** Counter staff usually identify a walk-in customer by phone number. */
    @Transactional(readOnly = true)
    public CustomerResponse findByPhone(String phone) {
        return customerRepository.findByPhoneInCompany(phone, companyScope.currentOrNull())
                .map(CustomerResponse::summary)
                .orElseThrow(() -> new NotFoundException("No customer with phone " + phone));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        // The registered branch, where given, says which company the customer
        // belongs to; it must agree with whatever the request or caller says.
        UUID companyId = request.registeredBranchId() != null && request.companyId() == null
                ? companyScope.resolveForCreate(companyScope.requireCompanyOfBranch(request.registeredBranchId()))
                : companyScope.resolveForCreate(request.companyId());
        if (request.registeredBranchId() != null) {
            companyScope.requireBranchInCompany(request.registeredBranchId(), companyId);
        }
        if (customerRepository.existsByCompanyIdAndPhone(companyId, request.phone().trim())) {
            throw new ConflictException("A customer with phone " + request.phone()
                    + " already exists");
        }
        String code = StringUtils.hasText(request.customerCode())
                ? request.customerCode().trim().toUpperCase()
                : uniqueCustomerCode(companyId);
        if (customerRepository.existsByCompanyIdAndCustomerCodeIgnoreCase(companyId, code)) {
            throw new ConflictException("Customer code already exists: " + code);
        }

        Customer customer = new Customer();
        customer.setCompanyId(companyId);
        customer.setCustomerCode(code);
        apply(customer, request);

        Customer saved = customerRepository.save(customer);
        auditService.record("CUSTOMER_CREATED", "Customer", saved.getId(), null,
                CustomerResponse.summary(saved), saved.getRegisteredBranchId());
        return CustomerResponse.detailed(saved);
    }

    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        Customer customer = requireCustomerEntity(id);
        CustomerResponse before = CustomerResponse.summary(customer);

        if (!customer.getPhone().equals(request.phone().trim())
                && customerRepository.existsByCompanyIdAndPhone(customer.getCompanyId(), request.phone().trim())) {
            throw new ConflictException("Another customer already uses phone " + request.phone());
        }
        if (request.registeredBranchId() != null) {
            companyScope.requireBranchInCompany(request.registeredBranchId(), customer.getCompanyId());
        }
        apply(customer, request);

        auditService.record("CUSTOMER_UPDATED", "Customer", id, before,
                CustomerResponse.summary(customer), customer.getRegisteredBranchId());
        return CustomerResponse.detailed(customer);
    }

    @Transactional
    public CustomerResponse addAddress(UUID id, CustomerAddressRequest request) {
        Customer customer = requireWithDetails(id);
        CustomerAddress address = new CustomerAddress();
        address.setAddressType(request.addressType());
        address.setAddressLine1(request.addressLine1().trim());
        address.setAddressLine2(request.addressLine2());
        address.setCity(request.city());
        address.setProvince(request.province());
        address.setPostalCode(request.postalCode());
        address.setCountry(request.country());
        address.setDefaultAddress(request.defaultAddress());
        if (request.defaultAddress()) {
            customer.getAddresses().forEach(a -> a.setDefaultAddress(false));
        }
        customer.addAddress(address);
        return CustomerResponse.detailed(customer);
    }

    @Transactional
    public CustomerResponse addDocument(UUID id, CustomerDocumentRequest request) {
        Customer customer = requireWithDetails(id);
        CustomerDocument document = new CustomerDocument();
        document.setDocumentType(request.documentType().trim().toUpperCase());
        document.setDocumentNumber(request.documentNumber().trim());
        document.setStorageKey(request.storageKey());
        document.setFileName(request.fileName());
        document.setIssueDate(request.issueDate());
        document.setExpiryDate(request.expiryDate());
        customer.addDocument(document);

        // Adding an identity document moves KYC into review, never straight to verified.
        if (customer.getKycStatus() == KycStatus.NOT_REQUIRED
                || customer.getKycStatus() == KycStatus.REJECTED
                || customer.getKycStatus() == KycStatus.EXPIRED) {
            customer.setKycStatus(KycStatus.PENDING);
        }

        auditService.record("CUSTOMER_DOCUMENT_ADDED", "Customer", id, null,
                Map.of("documentType", document.getDocumentType()),
                customer.getRegisteredBranchId());
        return CustomerResponse.detailed(customer);
    }

    /**
     * Records a KYC decision. Verification is a compliance action, so it is
     * always attributed and audited.
     */
    @Transactional
    public CustomerResponse decideKyc(UUID id, KycStatus decision, String reason) {
        if (decision != KycStatus.VERIFIED && decision != KycStatus.REJECTED) {
            throw new ValidationException("A KYC decision must be VERIFIED or REJECTED");
        }
        Customer customer = requireWithDetails(id);
        if (decision == KycStatus.VERIFIED && customer.getDocuments().isEmpty()) {
            throw new ValidationException("Cannot verify KYC without at least one document");
        }

        KycStatus previous = customer.getKycStatus();
        customer.setKycStatus(decision);
        if (decision == KycStatus.VERIFIED) {
            customer.setKycVerifiedAt(LocalDate.now());
            customer.setKycVerifiedBy(SecurityUtils.currentUsername().orElse("system"));
            customer.getDocuments().forEach(d -> d.setVerified(true));
        }

        auditService.record("CUSTOMER_KYC_DECIDED", "Customer", id,
                Map.of("kycStatus", previous),
                Map.of("kycStatus", decision, "reason", String.valueOf(reason)),
                customer.getRegisteredBranchId());
        return CustomerResponse.detailed(customer);
    }

    @Transactional
    public CustomerResponse setPreference(UUID id, PreferenceRequest request) {
        Customer customer = requireWithDetails(id);
        String key = request.key().trim().toUpperCase();
        customer.getPreferences().stream()
                .filter(p -> p.getPreferenceKey().equals(key))
                .findFirst()
                .ifPresentOrElse(
                        existing -> existing.setPreferenceValue(request.value()),
                        () -> {
                            CustomerPreference preference = new CustomerPreference();
                            preference.setPreferenceKey(key);
                            preference.setPreferenceValue(request.value());
                            customer.addPreference(preference);
                        });
        return CustomerResponse.detailed(customer);
    }

    @Transactional
    public CustomerResponse changeStatus(UUID id, CustomerStatus status, String reason) {
        Customer customer = requireCustomerEntity(id);
        CustomerStatus previous = customer.getStatus();
        customer.setStatus(status);
        auditService.record("CUSTOMER_STATUS_CHANGED", "Customer", id,
                Map.of("status", previous),
                Map.of("status", status, "reason", String.valueOf(reason)),
                customer.getRegisteredBranchId());
        return CustomerResponse.detailed(requireWithDetails(id));
    }

    // ---------- cross-module directory ----------

    @Override
    @Transactional(readOnly = true)
    public CustomerView requireCustomer(UUID customerId) {
        return toView(requireCustomerEntity(customerId));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerView requireTransactableCustomer(UUID customerId) {
        Customer customer = requireCustomerEntity(customerId);
        if (!customer.canTransact()) {
            throw new ValidationException("Customer " + customer.getCustomerCode() + " is "
                    + customer.getStatus() + " and cannot transact");
        }
        return toView(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<CustomerView> findSegmentCandidates(UUID branchId,
                                                              Integer birthdayMonth) {
        return customerRepository.findSegmentCandidates(companyScope.currentOrNull(), branchId, birthdayMonth)
                .stream()
                .map(this::toView)
                .toList();
    }

    // ---------- helpers ----------

    /** Another company's customer is reported as absent, never as forbidden. */
    private Customer requireCustomerEntity(UUID id) {
        return customerRepository.findByIdInCompany(id, companyScope.currentOrNull())
                .orElseThrow(() -> NotFoundException.of("Customer", id));
    }

    private Customer requireWithDetails(UUID id) {
        UUID scope = companyScope.currentOrNull();
        return (scope == null
                ? customerRepository.findWithDetailsById(id)
                : customerRepository.findWithDetailsByIdAndCompanyId(id, scope))
                .orElseThrow(() -> NotFoundException.of("Customer", id));
    }

    private CustomerView toView(Customer c) {
        return new CustomerView(c.getId(), c.getCustomerCode(), c.getFullName(), c.getPhone(),
                c.canTransact(), c.isKycVerified());
    }

    private void apply(Customer customer, CustomerRequest request) {
        customer.setCustomerType(request.customerType() == null
                ? CustomerType.INDIVIDUAL : request.customerType());
        customer.setFullName(request.fullName().trim());
        customer.setCompanyName(request.companyName());
        customer.setPhone(request.phone().trim());
        customer.setAlternatePhone(request.alternatePhone());
        customer.setEmail(request.email());
        customer.setDateOfBirth(request.dateOfBirth());
        customer.setAnniversaryDate(request.anniversaryDate());
        customer.setGender(request.gender());
        customer.setTaxNumber(request.taxNumber());
        customer.setRegisteredBranchId(request.registeredBranchId());
        customer.setNotes(request.notes());
    }

    private String uniqueCustomerCode(UUID companyId) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "CUS-" + CodeGenerator.random(8);
            if (!customerRepository.existsByCompanyIdAndCustomerCodeIgnoreCase(companyId, candidate)) {
                return candidate;
            }
        }
        throw new ConflictException("Could not allocate a unique customer code; retry the request");
    }
}
