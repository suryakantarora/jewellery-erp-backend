package com.finotech.jewellery.modules.procurement.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One line per physical piece delivered: each becomes a serialized item, so a
 * delivery of three identical rings is three lines, not a quantity of three.
 */
public record GoodsReceiptRequest(@NotNull UUID purchaseOrderId,
                                  LocalDate receiptDate,
                                  @Size(max = 100) String supplierDeliveryNote,
                                  @Size(max = 500) String notes,
                                  @NotEmpty @Valid List<Line> lines) {

    public record Line(@NotNull UUID purchaseOrderLineId,
                       @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal grossWeight,
                       @DecimalMin("0.0") BigDecimal stoneWeight,
                       @DecimalMin("0.0") BigDecimal purchaseCost,
                       @DecimalMin("0.0") BigDecimal makingCost,
                       @DecimalMin("0.0") BigDecimal stoneCost,
                       @Size(max = 50) String hallmarkNumber,
                       @Size(max = 100) String barcode,
                       @Size(max = 100) String rfidTag,
                       @Size(max = 255) String notes) {
    }
}
