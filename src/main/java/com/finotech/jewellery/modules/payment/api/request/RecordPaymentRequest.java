package com.finotech.jewellery.modules.payment.api.request;

import com.finotech.jewellery.modules.payment.domain.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Records money taken against a sale. Never send full card numbers: only the
 * last four digits are accepted, and only for display on a receipt.
 */
public record RecordPaymentRequest(@NotNull UUID saleId,
                                   @NotNull PaymentMethod method,
                                   @NotNull @DecimalMin(value = "0.0", inclusive = false)
                                   BigDecimal amount,
                                   @Size(max = 100) String transactionReference,
                                   @Pattern(regexp = "\\d{4}", message = "must be exactly 4 digits")
                                   String cardLastFour,
                                   @Size(max = 150) String bankName,
                                   @Size(max = 500) String notes) {
}
