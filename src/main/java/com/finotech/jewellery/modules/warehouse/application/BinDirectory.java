package com.finotech.jewellery.modules.warehouse.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * The published contract other modules use to reach storage bins.
 *
 * <p>Inventory needs to validate a bin before putting an item in it, but must
 * not query warehouse tables directly (dependency rule 5).
 */
public interface BinDirectory {

    /** @return a lightweight view of a bin, or throws if it does not exist */
    BinView requireBin(UUID binId);

    /** Bin codes for a batch of ids, so a list page labels its bins in one query. */
    Map<UUID, String> codesFor(Collection<UUID> binIds);

    record BinView(UUID id, UUID locationId, String code, String name, boolean active) {
    }
}
