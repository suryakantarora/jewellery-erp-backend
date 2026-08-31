package com.finotech.jewellery.modules.payment.api.response;

import com.finotech.jewellery.modules.payment.domain.entity.Payment;
import com.finotech.jewellery.modules.payment.domain.enums.PaymentDirection;
import com.finotech.jewellery.modules.payment.domain.enums.PaymentMethod;
import com.finotech.jewellery.modules.payment.domain.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(UUID id, String paymentNumber, UUID saleId, UUID customerId,
                              UUID branchId, PaymentDirection direction, PaymentMethod method,
                              PaymentStatus status, BigDecimal amount, String currency,
                              String transactionReference, String cardLastFour, String bankName,
                              UUID originalPaymentId, Instant capturedAt, String failureReason,
                              boolean reconciled, String notes, Instant createdAt,
                              String createdBy) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getPaymentNumber(), p.getSaleId(), p.getCustomerId(),
                p.getBranchId(), p.getDirection(), p.getMethod(), p.getStatus(), p.getAmount(),
                p.getCurrency(), p.getTransactionReference(), p.getCardLastFour(), p.getBankName(),
                p.getOriginalPaymentId(), p.getCapturedAt(), p.getFailureReason(), p.isReconciled(),
                p.getNotes(), p.getCreatedAt(), p.getCreatedBy());
    }
}
