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

    private SecurityUtils() {
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
     * Guards branch-scoped operations: a user may only act inside branches
     * granted to them, unless they are a super administrator.
     */
    public static void requireBranchAccess(UUID branchId) {
        if (!requireCurrentUser().hasAccessToBranch(branchId)) {
            throw new ForbiddenException("No access to branch " + branchId);
        }
    }
}
