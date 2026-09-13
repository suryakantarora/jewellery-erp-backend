package com.finotech.jewellery.modules.identity.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Published contract for modules that need to find <em>people</em> rather
 * than authorize a request: who should be told about something.
 *
 * <p>Deliberately narrow. Callers get ids, never entities, so nothing outside
 * identity can depend on how roles and permissions are stored.
 */
public interface UserDirectory {

    /**
     * Active users who hold the permission and work in at least one of the
     * branches. A super administrator qualifies only when they are actually
     * assigned to one of those branches: they can act everywhere, but that is
     * not a reason to page them about every branch.
     */
    List<UUID> activeUserIdsWithPermissionInBranches(String permission,
                                                    Collection<UUID> branchIds);

    /** Resolves an audit username (e.g. {@code created_by}) to a user id. */
    Optional<UUID> userIdForUsername(String username);
}
