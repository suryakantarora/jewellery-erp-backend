package com.finotech.jewellery.modules.payment.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record RefundRequest(@NotNull UUID originalPaymentId,
                            @NotNull @DecimalMin(value = "0.0", inclusive = false)
                            BigDecimal amount,
                            @NotBlank @Size(max = 500) String reason) {
}
