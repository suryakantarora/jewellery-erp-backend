package com.finotech.jewellery.modules.organization.api.controller;

import com.finotech.jewellery.modules.organization.api.request.BranchRequest;
import com.finotech.jewellery.modules.organization.api.request.CompanyRequest;
import com.finotech.jewellery.modules.organization.api.request.LocationRequest;
import com.finotech.jewellery.modules.organization.api.response.BranchResponse;
import com.finotech.jewellery.modules.organization.api.response.CompanyResponse;
import com.finotech.jewellery.modules.organization.api.response.LocationResponse;
import com.finotech.jewellery.modules.organization.application.service.OrganizationService;
import com.finotech.jewellery.modules.organization.domain.enums.LocationType;
import com.finotech.jewellery.shared.common.ApiResponse;
import com.finotech.jewellery.shared.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Organization")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrganizationController {

    private static final String VIEW = "hasAuthority('ORGANIZATION_VIEW')";
    private static final String MANAGE = "hasAuthority('ORGANIZATION_MANAGE')";

    private final OrganizationService organizationService;

    // ---------- companies ----------

    @Operation(summary = "List companies")
    @GetMapping("/companies")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> listCompanies() {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.listCompanies()));
    }

    @Operation(summary = "Get a company")
    @GetMapping("/companies/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompany(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.getCompany(id)));
    }

    @Operation(summary = "Create a company")
    @PostMapping("/companies")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CompanyResponse>> createCompany(
            @Valid @RequestBody CompanyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(organizationService.createCompany(request)));
    }

    @Operation(summary = "Update a company")
    @PutMapping("/companies/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CompanyResponse>> updateCompany(
            @PathVariable UUID id, @Valid @RequestBody CompanyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.updateCompany(id, request)));
    }

    // ---------- branches ----------

    @Operation(summary = "List branches")
    @GetMapping("/branches")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<BranchResponse>>> listBranches(
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                organizationService.searchBranches(companyId, search, pageable)));
    }

    @Operation(summary = "Get a branch")
    @GetMapping("/branches/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<BranchResponse>> getBranch(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.getBranch(id)));
    }

    @Operation(summary = "Create a branch")
    @PostMapping("/branches")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<BranchResponse>> createBranch(
            @Valid @RequestBody BranchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(organizationService.createBranch(request)));
    }

    @Operation(summary = "Update a branch")
    @PutMapping("/branches/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<BranchResponse>> updateBranch(
            @PathVariable UUID id, @Valid @RequestBody BranchRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.updateBranch(id, request)));
    }

    @Operation(summary = "Deactivate a branch")
    @DeleteMapping("/branches/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Void>> deactivateBranch(@PathVariable UUID id) {
        organizationService.deactivateBranch(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Branch deactivated"));
    }

    // ---------- locations ----------

    @Operation(summary = "List the locations of a branch")
    @GetMapping("/branches/{branchId}/locations")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<LocationResponse>>> listLocations(
            @PathVariable UUID branchId,
            @RequestParam(required = false) LocationType type) {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.listLocations(branchId, type)));
    }

    @Operation(summary = "Get a location")
    @GetMapping("/locations/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<LocationResponse>> getLocation(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.getLocation(id)));
    }

    @Operation(summary = "Create a location")
    @PostMapping("/locations")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<LocationResponse>> createLocation(
            @Valid @RequestBody LocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(organizationService.createLocation(request)));
    }

    @Operation(summary = "Update a location")
    @PutMapping("/locations/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<LocationResponse>> updateLocation(
            @PathVariable UUID id, @Valid @RequestBody LocationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.updateLocation(id, request)));
    }

    @Operation(summary = "Deactivate a location")
    @DeleteMapping("/locations/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Void>> deactivateLocation(@PathVariable UUID id) {
        organizationService.deactivateLocation(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Location deactivated"));
    }
}
