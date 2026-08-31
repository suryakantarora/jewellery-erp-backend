package com.finotech.jewellery.modules.supplier.api.controller;

import com.finotech.jewellery.modules.supplier.api.request.SupplierBankAccountRequest;
import com.finotech.jewellery.modules.supplier.api.request.SupplierContactRequest;
import com.finotech.jewellery.modules.supplier.api.request.SupplierRequest;
import com.finotech.jewellery.modules.supplier.api.response.SupplierResponse;
import com.finotech.jewellery.modules.supplier.application.service.SupplierService;
import com.finotech.jewellery.modules.supplier.domain.enums.SupplierStatus;
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

@Tag(name = "Suppliers")
@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private static final String VIEW = "hasAuthority('SUPPLIER_VIEW')";
    private static final String MANAGE = "hasAuthority('SUPPLIER_MANAGE')";

    private final SupplierService supplierService;

    @Operation(summary = "Search suppliers")
    @GetMapping
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<SupplierResponse>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SupplierStatus status,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.search(search, status, pageable)));
    }

    @Operation(summary = "Get a supplier with contacts and bank accounts")
    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<SupplierResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.get(id)));
    }

    @Operation(summary = "Create a supplier")
    @PostMapping
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<SupplierResponse>> create(
            @Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(supplierService.create(request)));
    }

    @Operation(summary = "Update a supplier")
    @PutMapping("/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<SupplierResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.update(id, request)));
    }

    @Operation(summary = "Change supplier status (block, reactivate)")
    @PostMapping("/{id}/status")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<SupplierResponse>> changeStatus(
            @PathVariable UUID id,
            @RequestParam SupplierStatus status,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.changeStatus(id, status, reason)));
    }

    @Operation(summary = "Add a contact")
    @PostMapping("/{id}/contacts")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<SupplierResponse>> addContact(
            @PathVariable UUID id, @Valid @RequestBody SupplierContactRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(supplierService.addContact(id, request)));
    }

    @Operation(summary = "Add a bank account")
    @PostMapping("/{id}/bank-accounts")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<SupplierResponse>> addBankAccount(
            @PathVariable UUID id, @Valid @RequestBody SupplierBankAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(supplierService.addBankAccount(id, request)));
    }
}
