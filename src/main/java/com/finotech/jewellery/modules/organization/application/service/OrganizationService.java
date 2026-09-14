package com.finotech.jewellery.modules.organization.application.service;

import com.finotech.jewellery.modules.organization.api.request.BranchRequest;
import com.finotech.jewellery.modules.organization.api.request.CompanyRequest;
import com.finotech.jewellery.modules.organization.api.request.LocationRequest;
import com.finotech.jewellery.modules.organization.api.response.BranchResponse;
import com.finotech.jewellery.modules.organization.api.response.CompanyResponse;
import com.finotech.jewellery.modules.organization.api.response.LocationResponse;
import com.finotech.jewellery.modules.organization.application.OrganizationDirectory;
import com.finotech.jewellery.modules.organization.domain.entity.Branch;
import com.finotech.jewellery.modules.organization.domain.entity.Company;
import com.finotech.jewellery.modules.organization.domain.entity.Location;
import com.finotech.jewellery.modules.organization.domain.enums.LocationType;
import com.finotech.jewellery.modules.organization.domain.enums.OrganizationStatus;
import com.finotech.jewellery.modules.organization.infrastructure.repository.BranchRepository;
import com.finotech.jewellery.modules.organization.infrastructure.repository.CompanyRepository;
import com.finotech.jewellery.modules.organization.infrastructure.repository.LocationRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.security.AuthenticatedUser;
import com.finotech.jewellery.shared.security.SecurityUtils;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Company, branch and location master data, plus the cross-module directory
 * other modules resolve locations through.
 */
@Service
@RequiredArgsConstructor
public class OrganizationService implements OrganizationDirectory {

    private final CompanyRepository companyRepository;
    private final BranchRepository branchRepository;
    private final LocationRepository locationRepository;
    private final AuditService auditService;

    // ---------- company ----------

    /**
     * Every company for a platform-level super administrator; otherwise only
     * the caller's own. Another tenant's existence is not for a tenant to see.
     */
    @Transactional(readOnly = true)
    public List<CompanyResponse> listCompanies() {
        UUID scope = SecurityUtils.currentCompanyIdOrNull();
        return companyRepository.findAll().stream()
                .filter(c -> scope == null || c.getId().equals(scope))
                .map(CompanyResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CompanyResponse getCompany(UUID id) {
        return CompanyResponse.from(requireVisibleCompany(id));
    }

    @Transactional
    public CompanyResponse createCompany(CompanyRequest request) {
        if (companyRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("Company code already exists: " + request.code());
        }
        Company company = new Company();
        applyCompany(company, request);
        Company saved = companyRepository.save(company);
        auditService.record("COMPANY_CREATED", "Company", saved.getId(), null, CompanyResponse.from(saved));
        return CompanyResponse.from(saved);
    }

    @Transactional
    public CompanyResponse updateCompany(UUID id, CompanyRequest request) {
        Company company = requireVisibleCompany(id);
        CompanyResponse before = CompanyResponse.from(company);
        applyCompany(company, request);
        CompanyResponse after = CompanyResponse.from(company);
        auditService.record("COMPANY_UPDATED", "Company", id, before, after);
        return after;
    }

    // ---------- branch ----------

    /** Branches of the caller's company; a super administrator may filter by any. */
    @Transactional(readOnly = true)
    public PageResponse<BranchResponse> searchBranches(UUID companyId, String search, Pageable pageable) {
        UUID scope = SecurityUtils.currentCompanyIdOrNull();
        if (scope != null && companyId != null && !scope.equals(companyId)) {
            return PageResponse.of(Page.empty(pageable), BranchResponse::from);
        }
        UUID effective = scope != null ? scope : companyId;
        return PageResponse.of(branchRepository.search(effective, search, pageable), BranchResponse::from);
    }

    /**
     * The branches the signed-in user may work in.
     *
     * <p>Needs no permission. {@code ORGANIZATION_VIEW} governs *browsing the
     * organisation* — an administrative concern — but every member of staff has
     * to know which branches are theirs simply to start working. Gating this on
     * it meant a sales executive, who has no reason to browse the org chart,
     * could not complete sign-in at all: the app cannot establish a session
     * without a branch.
     *
     * <p>A super administrator has an empty branch set precisely because they
     * may act everywhere, so they get all active branches.
     */
    @Transactional(readOnly = true)
    public List<BranchResponse> myBranches() {
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        if (user.superAdmin()) {
            return branchRepository.findAll().stream()
                    .filter(b -> b.getStatus() == OrganizationStatus.ACTIVE)
                    .filter(b -> user.companyId() == null || b.getCompany().getId().equals(user.companyId()))
                    .map(BranchResponse::from)
                    .toList();
        }
        return branchRepository.findAllByIdInCompany(user.branchIds(), user.companyId()).stream()
                .map(BranchResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BranchResponse getBranch(UUID id) {
        return BranchResponse.from(requireVisibleBranch(id));
    }

    @Transactional
    public BranchResponse createBranch(BranchRequest request) {
        if (branchRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("Branch code already exists: " + request.code());
        }
        Branch branch = new Branch();
        branch.setCompany(requireVisibleCompany(request.companyId()));
        applyBranch(branch, request);
        Branch saved = branchRepository.save(branch);
        auditService.record("BRANCH_CREATED", "Branch", saved.getId(), null,
                BranchResponse.from(saved), saved.getId());
        return BranchResponse.from(saved);
    }

    @Transactional
    public BranchResponse updateBranch(UUID id, BranchRequest request) {
        Branch branch = requireVisibleBranch(id);
        BranchResponse before = BranchResponse.from(branch);
        if (!branch.getCompany().getId().equals(request.companyId())) {
            throw new ValidationException("A branch cannot be moved to another company");
        }
        applyBranch(branch, request);
        BranchResponse after = BranchResponse.from(branch);
        auditService.record("BRANCH_UPDATED", "Branch", id, before, after, id);
        return after;
    }

    @Transactional
    public void deactivateBranch(UUID id) {
        Branch branch = requireVisibleBranch(id);
        branch.setStatus(OrganizationStatus.INACTIVE);
        auditService.record("BRANCH_DEACTIVATED", "Branch", id, null, null, id);
    }

    // ---------- location ----------

    @Transactional(readOnly = true)
    public List<LocationResponse> listLocations(UUID branchId, LocationType type) {
        requireVisibleBranch(branchId);
        List<Location> locations = type == null
                ? locationRepository.findAllByBranchId(branchId)
                : locationRepository.findAllByBranchIdAndType(branchId, type);
        return locations.stream().map(LocationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public LocationResponse getLocation(UUID id) {
        return LocationResponse.from(requireLocationEntity(id));
    }

    @Transactional
    public LocationResponse createLocation(LocationRequest request) {
        if (locationRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("Location code already exists: " + request.code());
        }
        Branch branch = requireVisibleBranch(request.branchId());

        Location location = new Location();
        location.setBranch(branch);
        location.setParent(resolveParent(request.parentId(), branch.getId()));
        applyLocation(location, request);

        Location saved = locationRepository.save(location);
        auditService.record("LOCATION_CREATED", "Location", saved.getId(), null,
                LocationResponse.from(saved), branch.getId());
        return LocationResponse.from(saved);
    }

    @Transactional
    public LocationResponse updateLocation(UUID id, LocationRequest request) {
        Location location = requireLocationEntity(id);
        LocationResponse before = LocationResponse.from(location);

        if (!location.getBranch().getId().equals(request.branchId())) {
            throw new ValidationException("A location cannot be moved to another branch");
        }
        if (id.equals(request.parentId())) {
            throw new ValidationException("A location cannot be its own parent");
        }
        location.setParent(resolveParent(request.parentId(), location.getBranch().getId()));
        applyLocation(location, request);

        LocationResponse after = LocationResponse.from(location);
        auditService.record("LOCATION_UPDATED", "Location", id, before, after,
                location.getBranch().getId());
        return after;
    }

    @Transactional
    public void deactivateLocation(UUID id) {
        Location location = requireLocationEntity(id);
        if (locationRepository.existsByParentId(id)) {
            throw new ValidationException("Deactivate or move child locations first");
        }
        location.setStatus(OrganizationStatus.INACTIVE);
        auditService.record("LOCATION_DEACTIVATED", "Location", id, null, null,
                location.getBranch().getId());
    }

    // ---------- cross-module directory ----------

    @Override
    @Transactional(readOnly = true)
    public LocationView requireLocation(UUID locationId) {
        Location location = requireLocationEntity(locationId);
        return toView(location);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocationView> monitoredLocations() {
        return locationRepository.findMonitored().stream().map(this::toView).toList();
    }

    private LocationView toView(Location location) {
        return new LocationView(location.getId(), location.getBranch().getId(), location.getCode(),
                location.getName(), location.getType(), location.isDualAuthorization(),
                location.getLowStockThreshold(), location.isActive());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean branchExists(UUID branchId) {
        return branchId != null && branchRepository.existsById(branchId);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<UUID> companyOfBranch(UUID branchId) {
        return branchId == null ? java.util.Optional.empty() : branchRepository.findCompanyIdById(branchId);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<String> companyName(UUID companyId) {
        return companyId == null ? java.util.Optional.empty()
                : companyRepository.findById(companyId).map(Company::getName);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<UUID, String> branchNames(java.util.Collection<UUID> branchIds) {
        if (branchIds == null || branchIds.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.Map<UUID, String> names = new java.util.HashMap<>();
        branchRepository.findAllById(branchIds).forEach(b -> names.put(b.getId(), b.getName()));
        return names;
    }

    // ---------- helpers ----------

    private Company requireCompany(UUID id) {
        return companyRepository.findById(id).orElseThrow(() -> NotFoundException.of("Company", id));
    }

    private Branch requireBranch(UUID id) {
        return branchRepository.findById(id).orElseThrow(() -> NotFoundException.of("Branch", id));
    }

    /** A company the caller may see; another tenant's is reported as absent, not forbidden. */
    private Company requireVisibleCompany(UUID id) {
        Company company = requireCompany(id);
        UUID scope = SecurityUtils.currentCompanyIdOrNull();
        if (scope != null && !scope.equals(company.getId())) {
            throw NotFoundException.of("Company", id);
        }
        return company;
    }

    private Branch requireVisibleBranch(UUID id) {
        Branch branch = requireBranch(id);
        UUID scope = SecurityUtils.currentCompanyIdOrNull();
        if (scope != null && !scope.equals(branch.getCompany().getId())) {
            throw NotFoundException.of("Branch", id);
        }
        return branch;
    }

    private Location requireLocationEntity(UUID id) {
        return locationRepository.findById(id).orElseThrow(() -> NotFoundException.of("Location", id));
    }

    private Location resolveParent(UUID parentId, UUID branchId) {
        if (parentId == null) {
            return null;
        }
        Location parent = requireLocationEntity(parentId);
        if (!parent.getBranch().getId().equals(branchId)) {
            throw new ValidationException("Parent location must belong to the same branch");
        }
        return parent;
    }

    private void applyCompany(Company company, CompanyRequest request) {
        company.setCode(request.code().trim().toUpperCase());
        company.setName(request.name().trim());
        company.setLegalName(request.legalName());
        company.setTaxNumber(request.taxNumber());
        company.setRegistrationNumber(request.registrationNumber());
        if (StringUtils.hasText(request.baseCurrency())) {
            company.setBaseCurrency(request.baseCurrency().toUpperCase());
        }
        company.setAddressLine(request.addressLine());
        company.setCity(request.city());
        company.setCountry(request.country());
        company.setPhone(request.phone());
        company.setEmail(request.email());
    }

    private void applyBranch(Branch branch, BranchRequest request) {
        branch.setCode(request.code().trim().toUpperCase());
        branch.setName(request.name().trim());
        branch.setHeadOffice(request.headOffice());
        branch.setAddressLine(request.addressLine());
        branch.setCity(request.city());
        branch.setCountry(request.country());
        branch.setPhone(request.phone());
        branch.setEmail(request.email());
        if (StringUtils.hasText(request.timezone())) {
            branch.setTimezone(request.timezone());
        }
    }

    private void applyLocation(Location location, LocationRequest request) {
        if (request.type() == LocationType.IN_TRANSIT) {
            throw new ValidationException("IN_TRANSIT is a virtual location and cannot be created");
        }
        location.setCode(request.code().trim().toUpperCase());
        location.setName(request.name().trim());
        location.setType(request.type());
        location.setDualAuthorization(request.dualAuthorization());
        location.setLowStockThreshold(request.lowStockThreshold());
        location.setDescription(request.description());
    }
}
