package com.finotech.jewellery.modules.inventory.api.request;

import java.util.UUID;

/**
 * Puts an item in a bin, or takes it out of one.
 *
 * <p>A null {@code binId} clears the assignment — an item on a showroom counter
 * legitimately sits in no bin, so that has to be expressible.
 */
public record AssignBinRequest(UUID binId) {
}
