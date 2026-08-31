package com.finotech.jewellery.modules.supplier.application;

import java.util.UUID;

/**
 * Published contract for Procurement and Finance: resolve a supplier without
 * reaching into supplier tables.
 */
public interface SupplierDirectory {

    SupplierView requireSupplier(UUID supplierId);

    /** Fails when the supplier is inactive or blocked. */
    SupplierView requireTradableSupplier(UUID supplierId);

    record SupplierView(UUID id, String code, String name, String currency,
                        Integer paymentTermsDays, boolean tradable) {
    }
}
