package com.finotech.jewellery.shared.security;

import java.util.Optional;
import java.util.UUID;

/**
 * The branch a request is scoped to, taken from the {@code X-Branch-Id} header.
 *
 * <p>This is the intended single interception point for branch scoping. Today
 * every branch-bound endpoint threads a {@code branchId} parameter through to
 * its repository and guards it with {@link SecurityUtils#requireBranchAccess};
 * the header lets a client state the branch it is operating in once, and lets
 * a future pass make scoping an interceptor concern instead of a parameter on
 * every method. Existing endpoints are untouched: only endpoints that opt in
 * (currently the dashboard summary) read it, and only as a fallback when no
 * explicit parameter is given.
 *
 * <p>{@link BranchContextFilter} populates the holder after JWT authentication,
 * having already verified the caller may act in that branch, and clears it when
 * the request completes. Code outside a request sees {@link Optional#empty()}.
 */
public final class BranchContext {

    public static final String HEADER = "X-Branch-Id";

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private BranchContext() {
    }

    public static Optional<UUID> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    static void set(UUID branchId) {
        if (branchId == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(branchId);
        }
    }

    static void clear() {
        CURRENT.remove();
    }
}
