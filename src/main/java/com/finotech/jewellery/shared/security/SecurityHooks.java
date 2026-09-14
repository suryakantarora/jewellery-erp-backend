package com.finotech.jewellery.shared.security;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Wires module-provided lookups into the static {@link SecurityUtils} once the
 * context is up. Static access is what lets services call
 * {@code SecurityUtils.requireBranchAccess(...)} without injecting anything.
 */
@Component
public class SecurityHooks {

    public SecurityHooks(Optional<BranchCompanyResolver> branchCompanyResolver) {
        SecurityUtils.setBranchCompanyResolver(branchCompanyResolver.orElse(null));
    }
}
