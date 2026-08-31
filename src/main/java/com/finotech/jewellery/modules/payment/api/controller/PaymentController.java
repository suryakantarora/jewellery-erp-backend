package com.finotech.jewellery.modules.payment.api.controller;

import com.finotech.jewellery.modules.payment.api.request.RecordPaymentRequest;
import com.finotech.jewellery.modules.payment.api.request.RefundRequest;
import com.finotech.jewellery.modules.payment.api.response.PaymentResponse;
import com.finotech.jewellery.modules.payment.application.service.PaymentService;
import com.finotech.jewellery.modules.payment.domain.enums.PaymentStatus;
import com.finotech.jewellery.shared.common.ApiResponse;
import com.finotech.jewellery.shared.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payments")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentController {

    private static final String VIEW = "hasAuthority('PAYMENT_VIEW')";
    private static final String COLLECT = "hasAuthority('PAYMENT_COLLECT')";
    private static final String REFUND = "hasAuthority('PAYMENT_REFUND')";
    private static final String RECONCILE = "hasAuthority('PAYMENT_RECONCILE')";

    private final PaymentService paymentService;

    @Operation(summary = "Search payments")
    @GetMapping("/payments")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<PaymentResponse>>> search(
            @RequestParam(required = false) UUID saleId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                paymentService.search(saleId, branchId, status, from, to, pageable)));
    }

    @Operation(summary = "List the payments against a sale")
    @GetMapping("/sales/{saleId}/payments")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> forSale(@PathVariable UUID saleId) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.forSale(saleId)));
    }

    @Operation(summary = "Take a payment against a sale",
            description = "Supports split payments: call once per method. When the sale "
                    + "becomes fully paid it is confirmed and its items are marked SOLD. "
                    + "Send X-Idempotency-Key so a retry or repeated gateway callback "
                    + "cannot take money twice.")
    @PostMapping("/sales/{saleId}/payments")
    @PreAuthorize(COLLECT)
    public ResponseEntity<ApiResponse<PaymentResponse>> pay(
            @PathVariable UUID saleId,
            @Valid @RequestBody RecordPaymentRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        RecordPaymentRequest scoped = new RecordPaymentRequest(saleId, request.method(),
                request.amount(), request.transactionReference(), request.cardLastFour(),
                request.bankName(), request.notes());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(paymentService.record(scoped, idempotencyKey)));
    }

    @Operation(summary = "Refund part or all of a captured payment")
    @PostMapping("/payments/refunds")
    @PreAuthorize(REFUND)
    public ResponseEntity<ApiResponse<PaymentResponse>> refund(
            @Valid @RequestBody RefundRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(paymentService.refund(request, idempotencyKey)));
    }

    @Operation(summary = "Mark a payment reconciled against a statement")
    @PostMapping("/payments/{id}/reconcile")
    @PreAuthorize(RECONCILE)
    public ResponseEntity<ApiResponse<PaymentResponse>> reconcile(
            @PathVariable UUID id,
            @RequestParam(required = false) String statementReference) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.reconcile(id, statementReference)));
    }
}
