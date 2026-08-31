package com.finotech.jewellery.modules.payment.application.service;

import com.finotech.jewellery.modules.payment.api.request.RecordPaymentRequest;
import com.finotech.jewellery.modules.payment.api.request.RefundRequest;
import com.finotech.jewellery.modules.payment.api.response.PaymentResponse;
import com.finotech.jewellery.modules.payment.domain.entity.Payment;
import com.finotech.jewellery.modules.payment.domain.enums.PaymentDirection;
import com.finotech.jewellery.modules.payment.domain.enums.PaymentStatus;
import com.finotech.jewellery.modules.payment.infrastructure.repository.PaymentRepository;
import com.finotech.jewellery.modules.sales.application.SaleSettlement;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.event.DomainEventPublisher;
import com.finotech.jewellery.shared.event.DomainEvents;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.security.SecurityUtils;
import com.finotech.jewellery.shared.utils.CodeGenerator;
import com.finotech.jewellery.shared.utils.MoneyUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Payments and refunds.
 *
 * <p>This module records money only. It reports the amount to Sales through
 * {@link SaleSettlement} and never changes inventory itself (dependency rule 4).
 *
 * <p>Duplicate protection is twofold: a replayed idempotency key returns the
 * original payment, and a unique constraint on that key means even a genuinely
 * concurrent retry cannot create a second row (section 12).
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final SaleSettlement saleSettlement;
    private final AuditService auditService;
    private final DomainEventPublisher events;

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> search(UUID saleId, UUID branchId, PaymentStatus status,
                                                LocalDate from, LocalDate to, Pageable pageable) {
        return PageResponse.of(paymentRepository.search(saleId, branchId, status, from, to, pageable),
                PaymentResponse::from);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> forSale(UUID saleId) {
        return paymentRepository.findAllBySaleIdOrderByCreatedAtAsc(saleId).stream()
                .map(PaymentResponse::from).toList();
    }

    /**
     * Captures a payment against a sale.
     *
     * @param idempotencyKey strongly recommended for card and gateway flows: a
     *                       repeated callback with the same key returns the
     *                       original payment rather than taking money twice
     */
    @Transactional
    public PaymentResponse record(RecordPaymentRequest request, String idempotencyKey) {
        if (StringUtils.hasText(idempotencyKey)) {
            var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return PaymentResponse.from(existing.get());
            }
        }

        SaleSettlement.SaleBalance balance = saleSettlement.balanceOf(request.saleId());
        if (!balance.acceptsPayment()) {
            throw new ConflictException("Sale is " + balance.status()
                    + " and no longer accepts payment");
        }
        SecurityUtils.requireBranchAccess(balance.branchId());

        BigDecimal amount = MoneyUtils.money(request.amount());
        if (amount.compareTo(balance.outstandingAmount()) > 0) {
            throw new ValidationException("Payment of " + amount
                    + " exceeds the outstanding balance of " + balance.outstandingAmount());
        }

        Payment payment = new Payment();
        payment.setPaymentNumber(CodeGenerator.reference("PAY"));
        payment.setSaleId(balance.saleId());
        payment.setCustomerId(balance.customerId());
        payment.setBranchId(balance.branchId());
        payment.setDirection(PaymentDirection.INBOUND);
        payment.setMethod(request.method());
        payment.setAmount(amount);
        payment.setCurrency(balance.currency());
        payment.setTransactionReference(request.transactionReference());
        payment.setCardLastFour(request.cardLastFour());
        payment.setBankName(request.bankName());
        payment.setNotes(request.notes());
        payment.setIdempotencyKey(StringUtils.hasText(idempotencyKey) ? idempotencyKey : null);
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setCapturedAt(Instant.now());

        Payment saved = persist(payment, idempotencyKey);

        // Sales decides what full settlement means; payment only reports the money.
        saleSettlement.applyPayment(balance.saleId(), amount);

        auditService.record("PAYMENT_CAPTURED", "Payment", saved.getId(), null,
                Map.of("saleId", String.valueOf(balance.saleId()),
                        "method", saved.getMethod(),
                        "amount", saved.getAmount()),
                saved.getBranchId());
        events.publish(new DomainEvents.PaymentReceived(saved.getId(), balance.saleId(),
                balance.customerId(), saved.getBranchId(), saved.getAmount(),
                saved.getMethod().name()));
        return PaymentResponse.from(saved);
    }

    /**
     * Refunds part or all of a captured payment, e.g. after a return.
     */
    @Transactional
    public PaymentResponse refund(RefundRequest request, String idempotencyKey) {
        if (StringUtils.hasText(idempotencyKey)) {
            var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return PaymentResponse.from(existing.get());
            }
        }

        Payment original = paymentRepository.findById(request.originalPaymentId())
                .orElseThrow(() -> NotFoundException.of("Payment", request.originalPaymentId()));
        if (original.getStatus() != PaymentStatus.CAPTURED) {
            throw new ConflictException("Only a captured payment can be refunded");
        }
        if (original.getDirection() == PaymentDirection.REFUND) {
            throw new ValidationException("A refund cannot itself be refunded");
        }
        SecurityUtils.requireBranchAccess(original.getBranchId());

        BigDecimal amount = MoneyUtils.money(request.amount());
        BigDecimal alreadyRefunded = paymentRepository
                .findAllBySaleIdOrderByCreatedAtAsc(original.getSaleId()).stream()
                .filter(p -> p.getDirection() == PaymentDirection.REFUND)
                .filter(p -> original.getId().equals(p.getOriginalPaymentId()))
                .filter(p -> p.getStatus() == PaymentStatus.CAPTURED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (alreadyRefunded.add(amount).compareTo(original.getAmount()) > 0) {
            throw new ValidationException("Refund exceeds the remaining refundable amount of "
                    + original.getAmount().subtract(alreadyRefunded));
        }

        Payment refund = new Payment();
        refund.setPaymentNumber(CodeGenerator.reference("REF"));
        refund.setSaleId(original.getSaleId());
        refund.setCustomerId(original.getCustomerId());
        refund.setBranchId(original.getBranchId());
        refund.setDirection(PaymentDirection.REFUND);
        refund.setMethod(original.getMethod());
        refund.setAmount(amount);
        refund.setCurrency(original.getCurrency());
        refund.setOriginalPaymentId(original.getId());
        refund.setNotes(request.reason());
        refund.setIdempotencyKey(StringUtils.hasText(idempotencyKey) ? idempotencyKey : null);
        refund.setStatus(PaymentStatus.CAPTURED);
        refund.setCapturedAt(Instant.now());

        Payment saved = persist(refund, idempotencyKey);
        saleSettlement.applyPayment(original.getSaleId(), amount.negate());

        auditService.record("PAYMENT_REFUNDED", "Payment", saved.getId(), null,
                Map.of("originalPaymentId", String.valueOf(original.getId()),
                        "amount", amount, "reason", request.reason()),
                saved.getBranchId());
        return PaymentResponse.from(saved);
    }

    /** Marks a payment as matched against a bank or gateway statement. */
    @Transactional
    public PaymentResponse reconcile(UUID id, String statementReference) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Payment", id));
        if (payment.getStatus() != PaymentStatus.CAPTURED) {
            throw new ConflictException("Only a captured payment can be reconciled");
        }
        payment.setReconciled(true);
        payment.setReconciledAt(Instant.now());
        if (StringUtils.hasText(statementReference)) {
            payment.setTransactionReference(statementReference);
        }
        auditService.record("PAYMENT_RECONCILED", "Payment", id, null,
                Map.of("statementReference", String.valueOf(statementReference)),
                payment.getBranchId());
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public List<Object[]> totalsByMethod(UUID branchId, LocalDate date) {
        return paymentRepository.totalsByMethod(branchId, date);
    }

    /**
     * Saves the payment, translating a unique-key collision into the original
     * row. This is the case where two identical requests race: one inserts, the
     * other must return the winner rather than fail.
     */
    private Payment persist(Payment payment, String idempotencyKey) {
        try {
            return paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException ex) {
            if (StringUtils.hasText(idempotencyKey)) {
                return paymentRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> ex);
            }
            throw ex;
        }
    }
}
