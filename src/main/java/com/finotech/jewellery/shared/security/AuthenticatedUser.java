package com.finotech.jewellery.shared.security;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The authenticated principal carried on every secured request. Branch scope is
 * part of the identity because most operations are branch-bound; the company
 * is the tenant boundary above it.
 *
 * <p>{@code companyId} is {@code null} for a super administrator, who is a
 * platform user and sees every company, and for an access token issued before
 * the claim existed. {@link SecurityUtils#currentCompanyIdOrNull()} tells the
 * two apart: a legacy token for an ordinary user is asked to sign in again.
 */
public record AuthenticatedUser(UUID userId,
                                String username,
                                Set<String> permissions,
                                Set<UUID> branchIds,
                                boolean superAdmin,
                                UUID companyId) {

    public Collection<GrantedAuthority> authorities() {
        return permissions.stream()
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p))
                .toList();
    }

    public boolean hasAccessToBranch(UUID branchId) {
        return superAdmin || (branchId != null && branchIds.contains(branchId));
    }

    /** True when the principal is confined to one company. */
    public boolean companyScoped() {
        return companyId != null;
    }
}
