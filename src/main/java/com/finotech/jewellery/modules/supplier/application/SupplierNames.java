package com.finotech.jewellery.modules.supplier.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Batch supplier name lookup for list screens in other modules. Unknown ids are
 * absent from the result rather than an error: a list row with no supplier
 * label is fine, a list that fails to render is not.
 */
public interface SupplierNames {

    Map<UUID, String> namesFor(Collection<UUID> supplierIds);
}
