package com.finotech.jewellery.modules.exchange.api.controller;

import com.finotech.jewellery.modules.exchange.api.request.ExchangeRequests;
import com.finotech.jewellery.modules.exchange.api.response.ExchangeResponse;
import com.finotech.jewellery.modules.exchange.application.service.ExchangeService;
import com.finotech.jewellery.modules.exchange.domain.enums.ExchangeStatus;
import com.finotech.jewellery.modules.exchange.domain.enums.ExchangeType;
import com.finotech.jewellery.shared.common.ApiResponse;
import com.finotech.jewellery.shared.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Exchange & Buyback")
@RestController
@RequestMapping("/api/v1/exchanges")
@RequiredArgsConstructor
public class ExchangeController {

    private static final String VIEW = "hasAuthority('EXCHANGE_VIEW')";
    private static final String PROCESS = "hasAuthority('EXCHANGE_PROCESS')";
    private static final String VALUE = "hasAuthority('EXCHANGE_VALUE')";
    private static final String APPROVE = "hasAuthority('EXCHANGE_APPROVE')";

    private final ExchangeService exchangeService;

    @Operation(summary = "Search exchange and buyback intakes")
    @GetMapping
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<ExchangeResponse>>> search(
            @RequestParam(required = false) ExchangeStatus status,
            @RequestParam(required = false) ExchangeType exchangeType,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID branchId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                exchangeService.search(status, exchangeType, customerId, branchId, pageable)));
    }

    @Operation(summary = "Get an intake with its full measurement and valuation trail")
    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<ExchangeResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(exchangeService.get(id)));
    }

    @Operation(summary = "Step 1 — take in old jewellery from a customer")
    @PostMapping
    @PreAuthorize(PROCESS)
    public ResponseEntity<ApiResponse<ExchangeResponse>> receive(
            @Valid @RequestBody ExchangeRequests.ReceiveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(exchangeService.receive(request)));
    }

    @Operation(summary = "Step 2 — record gross and stone weights")
    @PostMapping("/{id}/weigh")
    @PreAuthorize(PROCESS)
    public ResponseEntity<ApiResponse<ExchangeResponse>> weigh(
            @PathVariable UUID id, @Valid @RequestBody ExchangeRequests.WeighRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(exchangeService.weigh(id, request)));
    }

    @Operation(summary = "Step 3 — record the tested purity")
    @PostMapping("/{id}/purity-test")
    @PreAuthorize(PROCESS)
    public ResponseEntity<ApiResponse<ExchangeResponse>> testPurity(
            @PathVariable UUID id, @Valid @RequestBody ExchangeRequests.PurityTestRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(exchangeService.testPurity(id, request)));
    }

    @Operation(summary = "Step 4 — value at the buying rate and send for approval")
    @PostMapping("/{id}/valuation")
    @PreAuthorize(VALUE)
    public ResponseEntity<ApiResponse<ExchangeResponse>> value(
            @PathVariable UUID id, @Valid @RequestBody ExchangeRequests.ValuationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(exchangeService.value(id, request)));
    }

    @Operation(summary = "Step 5 — approve the valuation",
            description = "Must be a different user from the one who valued it.")
    @PostMapping("/{id}/approve")
    @PreAuthorize(APPROVE)
    public ResponseEntity<ApiResponse<ExchangeResponse>> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(exchangeService.approve(id)));
    }

    @Operation(summary = "Reject a valuation")
    @PostMapping("/{id}/reject")
    @PreAuthorize(APPROVE)
    public ResponseEntity<ApiResponse<ExchangeResponse>> reject(
            @PathVariable UUID id, @RequestParam String reason) {
        return ResponseEntity.ok(ApiResponse.ok(exchangeService.reject(id, reason)));
    }

    @Operation(summary = "Step 6 — settle the exchange or buyback")
    @PostMapping("/{id}/complete")
    @PreAuthorize(PROCESS)
    public ResponseEntity<ApiResponse<ExchangeResponse>> complete(
            @PathVariable UUID id, @Valid @RequestBody ExchangeRequests.CompleteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(exchangeService.complete(id, request)));
    }

    @Operation(summary = "Hand the piece back to the customer")
    @PostMapping("/{id}/return")
    @PreAuthorize(PROCESS)
    public ResponseEntity<ApiResponse<ExchangeResponse>> returnToCustomer(
            @PathVariable UUID id, @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.ok(exchangeService.returnToCustomer(id, reason)));
    }
}
