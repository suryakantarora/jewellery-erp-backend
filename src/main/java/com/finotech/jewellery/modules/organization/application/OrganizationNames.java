package com.finotech.jewellery.modules.organization.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Batch name lookups for other modules' list screens.
 *
 * <p>Separate from {@link OrganizationDirectory} on purpose: that port answers
 * "may I put stock here?" one location at a time, while this one exists so a
 * page of thirty inventory rows can label its branches and locations with two
 * queries instead of sixty. Unknown ids are simply absent from the result.
 */
public interface OrganizationNames {

    Map<UUID, String> branchNamesFor(Collection<UUID> branchIds);

    Map<UUID, String> locationNamesFor(Collection<UUID> locationIds);

    /** Every active branch, for callers (super administrators) who may see them all. */
    List<BranchRef> activeBranches();

    /** The given branches, active or not, for callers scoped to a fixed set. */
    List<BranchRef> branches(Collection<UUID> branchIds);

    record BranchRef(UUID id, String code, String name) {
    }
}
