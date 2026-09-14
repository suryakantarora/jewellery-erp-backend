package com.finotech.jewellery.modules.organization.application.service;

import com.finotech.jewellery.modules.organization.application.CompanyScope;
import com.finotech.jewellery.modules.organization.domain.entity.Branch;
import com.finotech.jewellery.modules.organization.infrastructure.repository.BranchRepository;
import com.finotech.jewellery.modules.organization.infrastructure.repository.CompanyRepository;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.security.AuthenticatedUser;
import com.finotech.jewellery.shared.security.BranchCompanyResolver;
import com.finotech.jewellery.shared.security.SecurityUtils;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the company a request acts for. See {@link CompanyScope}.
 *
 * <p>Also the {@link BranchCompanyResolver} behind
 * {@code SecurityUtils.requireBranchAccess}, so a branch grant can never reach
 * across companies.
 */
@Service
@RequiredArgsConstructor
public class CompanyScopeService implements CompanyScope, BranchCompanyResolver {

    private final CompanyRepository companyRepository;
    private final BranchRepository branchRepository;

    @Override
    public UUID currentOrNull() {
        return SecurityUtils.currentCompanyIdOrNull();
    }

    @Override
    @Transactional(readOnly = true)
    public UUID resolveForCreate(UUID requestedCompanyId) {
        Optional<AuthenticatedUser> current = SecurityUtils.currentUser();
        if (current.isPresent() && !current.get().superAdmin()) {
            UUID own = SecurityUtils.requireCompany();
            if (requestedCompanyId != null && !requestedCompanyId.equals(own)) {
                throw new ValidationException("Cannot create records for another company");
            }
            return own;
        }

        UUID home = current.map(AuthenticatedUser::companyId).orElse(null);
        if (home != null) {
            if (requestedCompanyId != null && !requestedCompanyId.equals(home)) {
                throw new ValidationException("Cannot create records for another company");
            }
            return home;
        }
        if (requestedCompanyId != null) {
            if (!companyRepository.existsById(requestedCompanyId)) {
                throw NotFoundException.of("Company", requestedCompanyId);
            }
            return requestedCompanyId;
        }
        Optional<UUID> fromBranch = SecurityUtils.currentBranchId().flatMap(this::companyOfBranch);
        if (fromBranch.isPresent()) {
            return fromBranch.get();
        }
        List<UUID> all = companyRepository.findAllIds();
        if (all.size() == 1) {
            return all.get(0);
        }
        throw new ValidationException(all.isEmpty()
                ? "No company exists yet; create one first"
                : "companyId is required: say which company this record belongs to");
    }

    @Override
    @Transactional(readOnly = true)
    public UUID requireCompanyOfBranch(UUID branchId) {
        return companyOfBranch(branchId).orElseThrow(() -> NotFoundException.of("Branch", branchId));
    }

    @Override
    @Transactional(readOnly = true)
    public void requireBranchInCompany(UUID branchId, UUID companyId) {
        UUID owner = companyOfBranch(branchId)
                .orElseThrow(() -> new ValidationException("Branch does not exist: " + branchId));
        if (!owner.equals(companyId)) {
            throw new ValidationException("Branch " + branchId + " belongs to another company");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> companyOfBranch(UUID branchId) {
        if (branchId == null) {
            return Optional.empty();
        }
        return branchRepository.findCompanyIdById(branchId);
    }
}
