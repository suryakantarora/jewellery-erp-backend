package com.finotech.jewellery.modules.procurement.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SupplierInvoiceRequest(@NotNull UUID supplierId,
                                     @NotBlank @Size(max = 100) String invoiceNumber,
                                     UUID purchaseOrderId,
                                     UUID goodsReceiptId,
                                     @NotNull LocalDate invoiceDate,
                                     LocalDate dueDate,
                                     @Size(min = 3, max = 3) String currency,
                                     @NotNull @DecimalMin("0.0") BigDecimal subTotal,
                                     @DecimalMin("0.0") BigDecimal taxAmount,
                                     @Size(max = 500) String notes) {
}
