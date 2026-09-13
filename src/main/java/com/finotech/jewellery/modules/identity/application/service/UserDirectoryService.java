package com.finotech.jewellery.modules.identity.application.service;

import com.finotech.jewellery.modules.identity.application.UserDirectory;
import com.finotech.jewellery.modules.identity.infrastructure.repository.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Serves {@link UserDirectory} straight from the repository: one query per
 * lookup, no entity graphs loaded, because the callers only want ids.
 */
@Service
@RequiredArgsConstructor
public class UserDirectoryService implements UserDirectory {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<UUID> activeUserIdsWithPermissionInBranches(String permission,
                                                           Collection<UUID> branchIds) {
        if (!StringUtils.hasText(permission) || branchIds == null) {
            return List.of();
        }
        Set<UUID> branches = branchIds.stream().filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (branches.isEmpty()) {
            // Hibernate cannot render an empty IN (), and there is nobody to find.
            return List.of();
        }
        return userRepository.findActiveIdsWithPermissionInBranches(permission, branches);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> userIdForUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return Optional.empty();
        }
        return userRepository.findIdByUsernameIgnoreCase(username.trim());
    }
}
