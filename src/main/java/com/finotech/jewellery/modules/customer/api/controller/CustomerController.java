package com.finotech.jewellery.modules.customer.api.controller;

import com.finotech.jewellery.modules.customer.api.request.CustomerAddressRequest;
import com.finotech.jewellery.modules.customer.api.request.CustomerDocumentRequest;
import com.finotech.jewellery.modules.customer.api.request.CustomerRequest;
import com.finotech.jewellery.modules.customer.api.request.PreferenceRequest;
import com.finotech.jewellery.modules.customer.api.response.CustomerResponse;
import com.finotech.jewellery.modules.customer.application.service.CustomerService;
import com.finotech.jewellery.modules.customer.domain.enums.CustomerStatus;
import com.finotech.jewellery.modules.customer.domain.enums.KycStatus;
import com.finotech.jewellery.shared.common.ApiResponse;
import com.finotech.jewellery.shared.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Customers")
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private static final String VIEW = "hasAuthority('CUSTOMER_VIEW')";
    private static final String MANAGE = "hasAuthority('CUSTOMER_MANAGE')";
    private static final String KYC = "hasAuthority('CUSTOMER_KYC_VERIFY')";

    private final CustomerService customerService;

    @Operation(summary = "Search customers")
    @GetMapping
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<CustomerResponse>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(required = false) KycStatus kycStatus,
            @RequestParam(required = false) UUID branchId,
            @PageableDefault(size = 20, sort = "fullName") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                customerService.search(search, status, kycStatus, branchId, pageable)));
    }

    @Operation(summary = "Look up a customer by phone number")
    @GetMapping("/by-phone")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<CustomerResponse>> byPhone(@RequestParam String phone) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.findByPhone(phone)));
    }

    @Operation(summary = "Get a customer with addresses, documents and preferences")
    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<CustomerResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.get(id)));
    }

    @Operation(summary = "Register a customer")
    @PostMapping
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CustomerResponse>> create(
            @Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(customerService.create(request)));
    }

    @Operation(summary = "Update a customer")
    @PutMapping("/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CustomerResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.update(id, request)));
    }

    @Operation(summary = "Add an address")
    @PostMapping("/{id}/addresses")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CustomerResponse>> addAddress(
            @PathVariable UUID id, @Valid @RequestBody CustomerAddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(customerService.addAddress(id, request)));
    }

    @Operation(summary = "Add an identity or KYC document")
    @PostMapping("/{id}/documents")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CustomerResponse>> addDocument(
            @PathVariable UUID id, @Valid @RequestBody CustomerDocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(customerService.addDocument(id, request)));
    }

    @Operation(summary = "Record a KYC decision")
    @PostMapping("/{id}/kyc")
    @PreAuthorize(KYC)
    public ResponseEntity<ApiResponse<CustomerResponse>> decideKyc(
            @PathVariable UUID id,
            @RequestParam KycStatus decision,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.decideKyc(id, decision, reason)));
    }

    @Operation(summary = "Set a customer preference")
    @PostMapping("/{id}/preferences")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CustomerResponse>> setPreference(
            @PathVariable UUID id, @Valid @RequestBody PreferenceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.setPreference(id, request)));
    }

    @Operation(summary = "Change customer status")
    @PostMapping("/{id}/status")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CustomerResponse>> changeStatus(
            @PathVariable UUID id,
            @RequestParam CustomerStatus status,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.changeStatus(id, status, reason)));
    }
}
