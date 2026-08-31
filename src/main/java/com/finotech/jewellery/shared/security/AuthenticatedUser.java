package com.finotech.jewellery.shared.security;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The authenticated principal carried on every secured request. Branch scope is
 * part of the identity because most operations are branch-bound.
 */
public record AuthenticatedUser(UUID userId,
                                String username,
                                Set<String> permissions,
                                Set<UUID> branchIds,
                                boolean superAdmin) {

    public Collection<GrantedAuthority> authorities() {
        return permissions.stream()
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p))
                .toList();
    }

    public boolean hasAccessToBranch(UUID branchId) {
        return superAdmin || (branchId != null && branchIds.contains(branchId));
    }
}
