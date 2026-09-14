package com.finotech.jewellery.shared.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Answers "which company owns this branch?" for {@link SecurityUtils}.
 *
 * <p>The shared layer cannot reach organization tables, so the organization
 * module publishes a bean of this type and {@link SecurityHooks} registers it
 * at startup. Empty when the branch is unknown, in which case no company check
 * is applied — the caller's own lookup will fail on the missing branch.
 */
public interface BranchCompanyResolver {

    Optional<UUID> companyOfBranch(UUID branchId);
}
