package com.finotech.jewellery.modules.pricing.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DiscountPolicyRequest(@NotBlank @Size(max = 40) String code,
                                    @NotBlank @Size(max = 150) String name,
                                    UUID branchId,
                                    @NotNull @DecimalMin("0.0")
                                    BigDecimal maxPercentageWithoutApproval,
                                    @NotNull @DecimalMin("0.0")
                                    BigDecimal maxPercentageWithApproval,
                                    boolean appliesToMakingCharge,
                                    boolean appliesToMetalValue,
                                    @NotNull LocalDate effectiveFrom,
                                    LocalDate effectiveTo) {
}
