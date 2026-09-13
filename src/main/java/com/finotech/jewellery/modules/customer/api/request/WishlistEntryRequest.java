package com.finotech.jewellery.modules.customer.api.request;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/** At least one of item / product / design. */
public record WishlistEntryRequest(UUID jewelleryItemId,
                                   UUID productId,
                                   UUID designId,
                                   @Size(max = 500) String note,
                                   UUID branchId) {
}
