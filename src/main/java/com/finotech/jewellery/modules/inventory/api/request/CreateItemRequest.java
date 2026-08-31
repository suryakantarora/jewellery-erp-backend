package com.finotech.jewellery.modules.inventory.api.request;

import com.finotech.jewellery.modules.gemstone.application.StoneRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Creates one serialized item. Net metal weight is derived, never supplied:
 * it is gross weight minus the weight of the stones set on the item.
 */
public record CreateItemRequest(@Size(max = 50) String itemCode,
                                @NotNull UUID productId,
                                UUID metalId,
                                UUID purityId,
                                @NotNull @DecimalMin(value = "0.0", inclusive = false)
                                BigDecimal grossWeight,
                                UUID sizeId,
                                @Size(max = 100) String rfidTag,
                                @Size(max = 100) String qrCode,
                                @Size(max = 100) String barcode,
                                @Size(max = 50) String hallmarkNumber,
                                @DecimalMin("0.0") BigDecimal purchaseCost,
                                @DecimalMin("0.0") BigDecimal makingCost,
                                @DecimalMin("0.0") BigDecimal stoneCost,
                                UUID supplierId,
                                LocalDate receivedDate,
                                @NotNull UUID locationId,
                                @Size(max = 500) String notes,
                                @Valid List<StoneRegistry.StoneSpec> stones) {
}
