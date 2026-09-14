package com.finotech.jewellery.shared.security;

import com.finotech.jewellery.shared.exception.ForbiddenException;
import com.finotech.jewellery.shared.exception.UnauthorizedException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Access to the current principal for services that need the acting user.
 */
public final class SecurityUtils {

    private static volatile BranchCompanyResolver branchCompanyResolver;

    private SecurityUtils() {
    }

    static void setBranchCompanyResolver(BranchCompanyResolver resolver) {
        branchCompanyResolver = resolver;
    }

    public static Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public static AuthenticatedUser requireCurrentUser() {
        return currentUser().orElseThrow(() -> new UnauthorizedException("No authenticated user in context"));
    }

    public static Optional<String> currentUsername() {
        return currentUser().map(AuthenticatedUser::username);
    }

    /**
     * The branch named by the request's {@code X-Branch-Id} header, already
     * verified against the caller's branch grants. Empty when the header was
     * not sent or outside a request. See {@link BranchContext}.
     */
    public static Optional<UUID> currentBranchId() {
        return BranchContext.current();
    }

    /**
     * The company the caller's reads must be confined to, or {@code null} when
     * they may see every company.
     *
     * <p>{@code null} for a super administrator without a home company and for
     * code running outside a request (schedulers, event listeners), which act
     * for the platform. An ordinary user whose token predates the company
     * claim is asked to sign in again rather than being shown everything.
     */
    public static UUID currentCompanyIdOrNull() {
        Optional<AuthenticatedUser> user = currentUser();
        if (user.isEmpty()) {
            return null;
        }
        AuthenticatedUser principal = user.get();
        if (principal.companyId() == null && !principal.superAdmin()) {
            throw new UnauthorizedException("Your session predates company scoping. Sign in again.");
        }
        return principal.companyId();
    }

    /** The caller's company, which an ordinary user always has. */
    public static UUID requireCompany() {
        AuthenticatedUser principal = requireCurrentUser();
        if (principal.companyId() == null) {
            throw new UnauthorizedException(principal.superAdmin()
                    ? "This action needs a company; the super administrator must name one"
                    : "Your session predates company scoping. Sign in again.");
        }
        return principal.companyId();
    }

    /**
     * Guards branch-scoped operations: a user may only act inside branches
     * granted to them, unless they are a super administrator. A branch of
     * another company is refused even when a grant names it — a grant can go
     * stale or be mis-assigned, and the company boundary must hold regardless.
     */
    public static void requireBranchAccess(UUID branchId) {
        AuthenticatedUser user = requireCurrentUser();
        if (!user.hasAccessToBranch(branchId)) {
            throw new ForbiddenException("No access to branch " + branchId);
        }
        if (user.companyId() != null && branchCompanyResolver != null) {
            branchCompanyResolver.companyOfBranch(branchId)
                    .filter(owner -> !owner.equals(user.companyId()))
                    .ifPresent(owner -> {
                        throw new ForbiddenException("No access to branch " + branchId);
                    });
        }
    }
}
