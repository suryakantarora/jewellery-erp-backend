package com.finotech.jewellery;

import com.finotech.jewellery.shared.security.AuthenticatedUser;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Places a principal in the security context for service-level tests.
 *
 * <p>{@code @WithMockUser} cannot be used here: the services read the platform's
 * own {@link AuthenticatedUser} principal for branch scope and permissions, not
 * a plain Spring user.
 */
public final class TestSecurity {

    private TestSecurity() {
    }

    /** A super administrator, which bypasses branch scoping. */
    public static void authenticateAsSuperAdmin() {
        authenticate(new AuthenticatedUser(UUID.randomUUID(), "test-admin",
                Set.of("DISCOUNT_APPROVE"), Set.of(), true));
    }

    /** A user limited to specific branches and permissions. */
    public static void authenticateAs(Set<String> permissions, Set<UUID> branchIds) {
        authenticate(new AuthenticatedUser(UUID.randomUUID(), "test-user",
                permissions, branchIds, false));
    }

    private static void authenticate(AuthenticatedUser user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.authorities()));
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
