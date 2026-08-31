package com.finotech.jewellery.modules.organization.domain.enums;

/**
 * Physical places an item can be. Modelled as one enum on a single Location
 * entity so Inventory can hold a uniform "current location" reference instead
 * of a polymorphic one.
 */
public enum LocationType {

    HEAD_OFFICE(false),
    CENTRAL_WAREHOUSE(true),
    BRANCH_WAREHOUSE(true),
    SHOWROOM(true),
    COUNTER(true),
    VAULT(true),
    STORE_ROOM(true),
    /** Virtual location representing goods moving between two real locations. */
    IN_TRANSIT(false);

    private final boolean stockHolding;

    LocationType(boolean stockHolding) {
        this.stockHolding = stockHolding;
    }

    /** Whether jewellery items may physically rest here. */
    public boolean isStockHolding() {
        return stockHolding;
    }
}
