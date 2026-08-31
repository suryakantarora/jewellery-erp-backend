package com.finotech.jewellery.modules.sales.api.controller;

import com.finotech.jewellery.modules.sales.api.request.CreateSaleRequest;
import com.finotech.jewellery.modules.sales.api.request.QuotationRequest;
import com.finotech.jewellery.modules.sales.api.request.SaleReturnRequest;
import com.finotech.jewellery.modules.sales.api.response.DailyClosingResponse;
import com.finotech.jewellery.modules.sales.api.response.QuotationResponse;
import com.finotech.jewellery.modules.sales.api.response.SaleResponse;
import com.finotech.jewellery.modules.sales.application.service.DailyClosingService;
import com.finotech.jewellery.modules.sales.application.service.QuotationService;
import com.finotech.jewellery.modules.sales.application.service.SaleService;
import com.finotech.jewellery.modules.sales.domain.enums.QuotationStatus;
import com.finotech.jewellery.modules.sales.domain.enums.SaleStatus;
import com.finotech.jewellery.shared.common.ApiResponse;
import com.finotech.jewellery.shared.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
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

@Tag(name = "Sales & POS")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SalesController {

    private static final String VIEW = "hasAuthority('SALE_VIEW')";
    private static final String CREATE = "hasAuthority('SALE_CREATE')";
    private static final String RETURN = "hasAuthority('SALE_RETURN')";

    private final SaleService saleService;
    private final QuotationService quotationService;
    private final DailyClosingService dailyClosingService;

    // ---------- quotations ----------

    @Operation(summary = "Search quotations")
    @GetMapping("/quotations")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<QuotationResponse>>> searchQuotations(
            @RequestParam(required = false) QuotationStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID branchId,
            @PageableDefault(size = 20, sort = "quotationDate") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                quotationService.search(status, customerId, branchId, pageable)));
    }

    @Operation(summary = "Get a quotation")
    @GetMapping("/quotations/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<QuotationResponse>> getQuotation(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(quotationService.get(id)));
    }

    @Operation(summary = "Issue a quotation",
            description = "Prices every item at today's rates and holds the figures until "
                    + "the validity date. It does not reserve stock.")
    @PostMapping("/quotations")
    @PreAuthorize(CREATE)
    public ResponseEntity<ApiResponse<QuotationResponse>> createQuotation(
            @Valid @RequestBody QuotationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(quotationService.create(request)));
    }

    @Operation(summary = "Cancel a quotation")
    @PostMapping("/quotations/{id}/cancel")
    @PreAuthorize(CREATE)
    public ResponseEntity<ApiResponse<QuotationResponse>> cancelQuotation(
            @PathVariable UUID id, @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.ok(quotationService.cancel(id, reason)));
    }

    // ---------- sales ----------

    @Operation(summary = "Search sales")
    @GetMapping("/sales")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<SaleResponse>>> searchSales(
            @RequestParam(required = false) SaleStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "saleDate") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                saleService.search(status, customerId, branchId, from, to, pageable)));
    }

    @Operation(summary = "Get a sale with its frozen price breakdown")
    @GetMapping("/sales/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<SaleResponse>> getSale(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(saleService.get(id)));
    }

    @Operation(summary = "Open a sale and hold its items",
            description = "Items are reserved and priced now. They are only marked SOLD once "
                    + "the sale is fully paid. Send X-Idempotency-Key to make retries safe.")
    @PostMapping("/sales")
    @PreAuthorize(CREATE)
    public ResponseEntity<ApiResponse<SaleResponse>> createSale(
            @Valid @RequestBody CreateSaleRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(saleService.create(request, idempotencyKey)));
    }

    @Operation(summary = "Cancel an unpaid sale and release its items")
    @PostMapping("/sales/{id}/cancel")
    @PreAuthorize(CREATE)
    public ResponseEntity<ApiResponse<SaleResponse>> cancelSale(
            @PathVariable UUID id, @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.ok(saleService.cancel(id, reason)));
    }

    @Operation(summary = "Mark a confirmed sale as handed to the customer")
    @PostMapping("/sales/{id}/deliver")
    @PreAuthorize(CREATE)
    public ResponseEntity<ApiResponse<SaleResponse>> deliverSale(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(saleService.deliver(id)));
    }

    @Operation(summary = "Return sold items to stock",
            description = "Returns the goods. Refund the money separately through the "
                    + "payment endpoints.")
    @PostMapping("/sales/{id}/returns")
    @PreAuthorize(RETURN)
    public ResponseEntity<ApiResponse<SaleResponse>> returnItems(
            @PathVariable UUID id, @Valid @RequestBody SaleReturnRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(saleService.returnItems(id, request)));
    }

    @Operation(summary = "Daily closing figures for a branch")
    @GetMapping("/sales/daily-closing")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<DailyClosingResponse>> dailyClosing(
            @RequestParam UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok(dailyClosingService.closingFor(branchId, date)));
    }
}
