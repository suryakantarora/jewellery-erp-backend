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
 *
 * <p>Ordinary users carry a company. The two-argument {@link #authenticateAs}
 * overloads use the company of the most recent {@link CommerceFixture} world,
 * which is what nearly every test means; tests that build their own company
 * pass it explicitly.
 */
public final class TestSecurity {

    private static volatile UUID defaultCompanyId;

    private TestSecurity() {
    }

    /** A platform super administrator: no home company, sees every company. */
    public static void authenticateAsSuperAdmin() {
        authenticate(new AuthenticatedUser(UUID.randomUUID(), "test-admin",
                Set.of("DISCOUNT_APPROVE"), Set.of(), true, null));
    }

    /** A user limited to specific branches and permissions, in the default company. */
    public static void authenticateAs(Set<String> permissions, Set<UUID> branchIds) {
        authenticateInCompany(defaultCompanyId, permissions, branchIds);
    }

    /** A specific user id, for tests that assert per-user scoping. */
    public static void authenticateAs(UUID userId, Set<String> permissions, Set<UUID> branchIds) {
        authenticate(new AuthenticatedUser(userId, "test-user-" + userId, permissions,
                branchIds, false, defaultCompanyId));
    }

    /** A user of a named company. */
    public static void authenticateInCompany(UUID companyId, Set<String> permissions, Set<UUID> branchIds) {
        authenticate(new AuthenticatedUser(UUID.randomUUID(), "test-user",
                permissions, branchIds, false, companyId));
    }

    /** The company the two-argument overloads scope users to. */
    public static void defaultCompany(UUID companyId) {
        defaultCompanyId = companyId;
    }

    private static void authenticate(AuthenticatedUser user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.authorities()));
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
