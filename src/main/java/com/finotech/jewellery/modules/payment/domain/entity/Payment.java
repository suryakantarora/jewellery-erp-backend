package com.finotech.jewellery.modules.payment.domain.entity;

import com.finotech.jewellery.modules.payment.domain.enums.PaymentDirection;
import com.finotech.jewellery.modules.payment.domain.enums.PaymentMethod;
import com.finotech.jewellery.modules.payment.domain.enums.PaymentStatus;
import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One payment against a sale. A sale can have many, which is how split payments
 * (part cash, part card) are represented.
 *
 * <p>Payment never touches inventory (dependency rule 4): it records money and
 * reports the sale's paid total; Sales decides when that means the goods leave.
 *
 * <p>{@code idempotencyKey} is unique, so a retried request or a repeated
 * gateway callback cannot create a duplicate payment (section 12).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "payment", schema = "payment")
public class Payment extends BaseEntity {

    @Column(name = "payment_number", nullable = false, unique = true, length = 50)
    private String paymentNumber;

    @Column(name = "sale_id", nullable = false)
    private UUID saleId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 20)
    private PaymentDirection direction = PaymentDirection.INBOUND;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 30)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "LAK";

    /** Bank or gateway reference; the card PAN is never stored. */
    @Column(name = "transaction_reference", length = 100)
    private String transactionReference;

    @Column(name = "card_last_four", length = 4)
    private String cardLastFour;

    @Column(name = "bank_name", length = 150)
    private String bankName;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    /** Set on a refund, pointing at the payment being reversed. */
    @Column(name = "original_payment_id")
    private UUID originalPaymentId;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "reconciled", nullable = false)
    private boolean reconciled;

    @Column(name = "reconciled_at")
    private Instant reconciledAt;

    @Column(name = "notes", length = 500)
    private String notes;

    /** Amount that counts toward the sale: refunds count negatively. */
    public BigDecimal signedAmount() {
        return direction == PaymentDirection.REFUND ? amount.negate() : amount;
    }
}
