package com.finotech.jewellery.modules.pricing.api.request;

import com.finotech.jewellery.modules.pricing.domain.enums.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CalculatePriceRequest(@NotNull UUID jewelleryItemId,
                                    UUID customerId,
                                    UUID branchId,
                                    DiscountType discountType,
                                    @DecimalMin("0.0") BigDecimal discountValue,
                                    boolean discountApproved) {
}
