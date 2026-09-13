package com.finotech.jewellery.modules.sales.api.controller;

import com.finotech.jewellery.modules.sales.api.request.DiscountRequestRequests.CreateDiscountRequest;
import com.finotech.jewellery.modules.sales.api.request.DiscountRequestRequests.DecisionNoteRequest;
import com.finotech.jewellery.modules.sales.api.response.DiscountRequestResponse;
import com.finotech.jewellery.modules.sales.application.service.DiscountRequestService;
import com.finotech.jewellery.modules.sales.domain.enums.DiscountRequestStatus;
import com.finotech.jewellery.shared.common.ApiResponse;
import com.finotech.jewellery.shared.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

@Tag(name = "Sales & POS")
@RestController
@RequestMapping("/api/v1/sales/discount-requests")
@RequiredArgsConstructor
public class DiscountRequestController {

    private static final String REQUEST = "hasAuthority('DISCOUNT_REQUEST')";
    private static final String APPROVE = "hasAuthority('DISCOUNT_APPROVE')";
    private static final String REQUEST_OR_APPROVE =
            "hasAnyAuthority('DISCOUNT_REQUEST', 'DISCOUNT_APPROVE')";

    private final DiscountRequestService discountRequestService;

    @Operation(summary = "Search discount requests",
            description = "mine=true limits the list to requests raised by the caller.")
    @GetMapping
    @PreAuthorize(REQUEST_OR_APPROVE)
    public ResponseEntity<ApiResponse<PageResponse<DiscountRequestResponse>>> search(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) DiscountRequestStatus status,
            @RequestParam(required = false, defaultValue = "false") boolean mine,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                discountRequestService.search(branchId, status, mine, pageable)));
    }

    @Operation(summary = "Get a discount request")
    @GetMapping("/{id}")
    @PreAuthorize(REQUEST_OR_APPROVE)
    public ResponseEntity<ApiResponse<DiscountRequestResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(discountRequestService.get(id)));
    }

    @Operation(summary = "Ask for a discount beyond the branch policy",
            description = "Exactly one of requestedPercentage / requestedAmount. Once approved, "
                    + "pass the id as discountRequestId when opening the sale.")
    @PostMapping
    @PreAuthorize(REQUEST)
    public ResponseEntity<ApiResponse<DiscountRequestResponse>> create(
            @Valid @RequestBody CreateDiscountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(discountRequestService.create(request)));
    }

    @Operation(summary = "Approve a discount request",
            description = "The approver must not be the person who raised it.")
    @PostMapping("/{id}/approve")
    @PreAuthorize(APPROVE)
    public ResponseEntity<ApiResponse<DiscountRequestResponse>> approve(
            @PathVariable UUID id, @Valid @RequestBody(required = false) DecisionNoteRequest body) {
        return ResponseEntity.ok(ApiResponse.ok(
                discountRequestService.approve(id, body == null ? null : body.reason())));
    }

    @Operation(summary = "Reject a discount request")
    @PostMapping("/{id}/reject")
    @PreAuthorize(APPROVE)
    public ResponseEntity<ApiResponse<DiscountRequestResponse>> reject(
            @PathVariable UUID id, @Valid @RequestBody DecisionNoteRequest body) {
        return ResponseEntity.ok(ApiResponse.ok(discountRequestService.reject(id, body.reason())));
    }

    @Operation(summary = "Withdraw a pending discount request (requester only)")
    @PostMapping("/{id}/cancel")
    @PreAuthorize(REQUEST)
    public ResponseEntity<ApiResponse<DiscountRequestResponse>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(discountRequestService.cancel(id)));
    }
}
