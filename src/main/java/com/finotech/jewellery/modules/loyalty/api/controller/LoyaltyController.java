package com.finotech.jewellery.modules.loyalty.api.controller;

import com.finotech.jewellery.modules.loyalty.api.request.LoyaltyRequests;
import com.finotech.jewellery.modules.loyalty.api.response.LoyaltyResponses.AccountResponse;
import com.finotech.jewellery.modules.loyalty.api.response.LoyaltyResponses.ProgramResponse;
import com.finotech.jewellery.modules.loyalty.api.response.LoyaltyResponses.TransactionResponse;
import com.finotech.jewellery.modules.loyalty.application.service.LoyaltyProgramService;
import com.finotech.jewellery.modules.loyalty.application.service.LoyaltyService;
import com.finotech.jewellery.shared.common.ApiResponse;
import com.finotech.jewellery.shared.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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

@Tag(name = "Loyalty")
@RestController
@RequestMapping("/api/v1/loyalty")
@RequiredArgsConstructor
public class LoyaltyController {

    private static final String VIEW = "hasAuthority('LOYALTY_VIEW')";
    private static final String MANAGE = "hasAuthority('LOYALTY_MANAGE')";
    private static final String REDEEM = "hasAuthority('LOYALTY_REDEEM')";
    private static final String ADJUST = "hasAuthority('LOYALTY_ADJUST')";

    private final LoyaltyService loyaltyService;
    private final LoyaltyProgramService programService;

    // ---------- programs ----------

    @Operation(summary = "List loyalty programs and their tiers")
    @GetMapping("/programs")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<ProgramResponse>>> listPrograms() {
        return ResponseEntity.ok(ApiResponse.ok(programService.list()));
    }

    @Operation(summary = "Get a loyalty program")
    @GetMapping("/programs/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<ProgramResponse>> getProgram(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(programService.get(id)));
    }

    @Operation(summary = "Create a loyalty program with its tiers")
    @PostMapping("/programs")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ProgramResponse>> createProgram(
            @Valid @RequestBody LoyaltyRequests.ProgramRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(programService.create(request)));
    }

    @Operation(summary = "Enable or disable a program")
    @PostMapping("/programs/{id}/active")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ProgramResponse>> setActive(
            @PathVariable UUID id, @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.ok(programService.setActive(id, active)));
    }

    // ---------- accounts ----------

    @Operation(summary = "Get a customer's loyalty standing")
    @GetMapping("/accounts/{customerId}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<AccountResponse>> getAccount(@PathVariable UUID customerId) {
        return ResponseEntity.ok(ApiResponse.ok(loyaltyService.getAccount(customerId)));
    }

    @Operation(summary = "Point statement for a customer")
    @GetMapping("/accounts/{customerId}/transactions")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> statement(
            @PathVariable UUID customerId,
            @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(loyaltyService.statement(customerId, pageable)));
    }

    @Operation(summary = "Enrol a customer",
            description = "Idempotent: enrolling an existing member returns their account.")
    @PostMapping("/accounts")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AccountResponse>> enrol(
            @Valid @RequestBody LoyaltyRequests.EnrolRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(loyaltyService.enrol(request.customerId())));
    }

    @Operation(summary = "Redeem points")
    @PostMapping("/redemptions")
    @PreAuthorize(REDEEM)
    public ResponseEntity<ApiResponse<AccountResponse>> redeem(
            @Valid @RequestBody LoyaltyRequests.RedeemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(loyaltyService.redeem(request)));
    }

    @Operation(summary = "Manually adjust a point balance",
            description = "Restricted and always audited; a reason is required.")
    @PostMapping("/adjustments")
    @PreAuthorize(ADJUST)
    public ResponseEntity<ApiResponse<AccountResponse>> adjust(
            @Valid @RequestBody LoyaltyRequests.AdjustRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(loyaltyService.adjust(request)));
    }
}
