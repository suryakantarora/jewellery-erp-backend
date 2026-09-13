package com.finotech.jewellery.modules.inventory.api.response;

import java.util.List;
import java.util.UUID;

/**
 * How many pieces of one product each branch holds, so a sales executive can
 * say "none here, but two in Pakse" without phoning. Every branch the caller
 * may see is listed, including those with nothing, because "0 in Pakse" is an
 * answer and a missing row is not.
 */
public record ProductAvailabilityResponse(UUID productId, String productName,
                                          List<BranchAvailability> branches) {

    /**
     * @param available pieces that can be sold right now ({@code AVAILABLE})
     * @param total     pieces the branch holds in any non-terminal state,
     *                  i.e. everything except SOLD and SCRAPPED
     */
    public record BranchAvailability(UUID branchId, String branchName, long available, long total) {
    }
}
