package com.finotech.jewellery.modules.pricing.api.request;

import com.finotech.jewellery.modules.pricing.domain.enums.ChargeType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MakingChargeRuleRequest(@NotBlank @Size(max = 40) String code,
                                      @NotBlank @Size(max = 150) String name,
                                      UUID productId,
                                      UUID productTypeId,
                                      UUID metalId,
                                      UUID purityId,
                                      UUID branchId,
                                      @NotNull ChargeType chargeType,
                                      @NotNull @DecimalMin("0.0") BigDecimal chargeValue,
                                      @DecimalMin("0.0") BigDecimal wastagePercentage,
                                      @DecimalMin("0.0") BigDecimal minCharge,
                                      @DecimalMin("0.0") BigDecimal maxCharge,
                                      @NotNull LocalDate effectiveFrom,
                                      LocalDate effectiveTo,
                                      int priority) {
}
