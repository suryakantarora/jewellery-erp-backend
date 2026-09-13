package com.finotech.jewellery.modules.organization.application.service;

import com.finotech.jewellery.modules.organization.application.OrganizationNames;
import com.finotech.jewellery.modules.organization.domain.entity.Branch;
import com.finotech.jewellery.modules.organization.domain.entity.Location;
import com.finotech.jewellery.modules.organization.domain.enums.OrganizationStatus;
import com.finotech.jewellery.modules.organization.infrastructure.repository.BranchRepository;
import com.finotech.jewellery.modules.organization.infrastructure.repository.LocationRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serves {@link OrganizationNames}. Kept apart from {@code OrganizationService}
 * so the batch lookups depend on the two repositories alone.
 */
@Service
@RequiredArgsConstructor
public class OrganizationNamesService implements OrganizationNames {

    private final BranchRepository branchRepository;
    private final LocationRepository locationRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> branchNamesFor(Collection<UUID> branchIds) {
        if (branchIds == null || branchIds.isEmpty()) {
            return Map.of();
        }
        return branchRepository.findAllById(branchIds).stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getName));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> locationNamesFor(Collection<UUID> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            return Map.of();
        }
        return locationRepository.findAllById(locationIds).stream()
                .collect(Collectors.toMap(Location::getId, Location::getName));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchRef> activeBranches() {
        return branchRepository.findAll().stream()
                .filter(b -> b.getStatus() == OrganizationStatus.ACTIVE)
                .map(this::toRef)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchRef> branches(Collection<UUID> branchIds) {
        if (branchIds == null || branchIds.isEmpty()) {
            return List.of();
        }
        return branchRepository.findAllById(branchIds).stream().map(this::toRef).toList();
    }

    private BranchRef toRef(Branch b) {
        return new BranchRef(b.getId(), b.getCode(), b.getName());
    }
}
