package com.finotech.jewellery.modules.customer.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WishlistEntryResponse(UUID id, UUID customerId, UUID jewelleryItemId,
                                    UUID productId, UUID designId, String itemCode,
                                    String productName, String designName,
                                    BigDecimal currentPrice, String currency, String itemStatus,
                                    String primaryImageKey, String note, String addedBy,
                                    Instant createdAt) {
}
