package com.finotech.jewellery.modules.pricing.api.controller;

import com.finotech.jewellery.modules.pricing.api.request.CalculatePriceRequest;
import com.finotech.jewellery.modules.pricing.api.request.DiscountPolicyRequest;
import com.finotech.jewellery.modules.pricing.api.request.MakingChargeRuleRequest;
import com.finotech.jewellery.modules.pricing.api.request.TaxRateRequest;
import com.finotech.jewellery.modules.pricing.api.response.PricingRuleResponse;
import com.finotech.jewellery.modules.pricing.application.PricingCalculator;
import com.finotech.jewellery.modules.pricing.application.service.PricingConfigService;
import com.finotech.jewellery.shared.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Pricing")
@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {

    private static final String VIEW = "hasAuthority('PRODUCT_VIEW')";
    private static final String CHANGE = "hasAuthority('PRICE_CHANGE')";

    private final PricingCalculator pricingCalculator;
    private final PricingConfigService configService;

    @Operation(summary = "Calculate the price of an item",
            description = "Returns a full breakdown: metal value, wastage, making charge, "
                    + "stone value, tax and discount. A discount beyond the branch policy is "
                    + "rejected unless discountApproved is set by a user holding DISCOUNT_APPROVE.")
    @PostMapping("/calculate")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PricingCalculator.PriceBreakdown>> calculate(
            @Valid @RequestBody CalculatePriceRequest request) {
        var breakdown = pricingCalculator.calculate(new PricingCalculator.PriceRequest(
                request.jewelleryItemId(), request.customerId(), request.branchId(),
                request.discountType() == null ? null : request.discountType().name(),
                request.discountValue(), request.discountApproved()));
        return ResponseEntity.ok(ApiResponse.ok(breakdown));
    }

    // ---------- configuration ----------

    @Operation(summary = "List making-charge rules")
    @GetMapping("/making-charge-rules")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<PricingRuleResponse>>> listMakingChargeRules() {
        return ResponseEntity.ok(ApiResponse.ok(configService.listMakingChargeRules()));
    }

    @Operation(summary = "Create a making-charge rule")
    @PostMapping("/making-charge-rules")
    @PreAuthorize(CHANGE)
    public ResponseEntity<ApiResponse<PricingRuleResponse>> createMakingChargeRule(
            @Valid @RequestBody MakingChargeRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(configService.createMakingChargeRule(request)));
    }

    @Operation(summary = "Retire a making-charge rule")
    @DeleteMapping("/making-charge-rules/{id}")
    @PreAuthorize(CHANGE)
    public ResponseEntity<ApiResponse<PricingRuleResponse>> deactivateMakingChargeRule(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(configService.deactivateMakingChargeRule(id)));
    }

    @Operation(summary = "List tax rates")
    @GetMapping("/tax-rates")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<PricingRuleResponse>>> listTaxRates() {
        return ResponseEntity.ok(ApiResponse.ok(configService.listTaxRates()));
    }

    @Operation(summary = "Create a tax rate")
    @PostMapping("/tax-rates")
    @PreAuthorize(CHANGE)
    public ResponseEntity<ApiResponse<PricingRuleResponse>> createTaxRate(
            @Valid @RequestBody TaxRateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(configService.createTaxRate(request)));
    }

    @Operation(summary = "Retire a tax rate")
    @DeleteMapping("/tax-rates/{id}")
    @PreAuthorize(CHANGE)
    public ResponseEntity<ApiResponse<PricingRuleResponse>> deactivateTaxRate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(configService.deactivateTaxRate(id)));
    }

    @Operation(summary = "List discount policies")
    @GetMapping("/discount-policies")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<PricingRuleResponse>>> listDiscountPolicies() {
        return ResponseEntity.ok(ApiResponse.ok(configService.listDiscountPolicies()));
    }

    @Operation(summary = "Create a discount policy")
    @PostMapping("/discount-policies")
    @PreAuthorize(CHANGE)
    public ResponseEntity<ApiResponse<PricingRuleResponse>> createDiscountPolicy(
            @Valid @RequestBody DiscountPolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(configService.createDiscountPolicy(request)));
    }
}
