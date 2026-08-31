package com.finotech.jewellery.modules.metal.api.controller;

import com.finotech.jewellery.modules.metal.api.request.MetalRequest;
import com.finotech.jewellery.modules.metal.api.request.PublishRateRequest;
import com.finotech.jewellery.modules.metal.api.request.PurityRequest;
import com.finotech.jewellery.modules.metal.api.response.MetalRateResponse;
import com.finotech.jewellery.modules.metal.api.response.MetalResponse;
import com.finotech.jewellery.modules.metal.api.response.PurityResponse;
import com.finotech.jewellery.modules.metal.application.service.MetalRateService;
import com.finotech.jewellery.modules.metal.application.service.MetalService;
import com.finotech.jewellery.modules.metal.domain.enums.RateType;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Metals & Rates")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MetalController {

    private static final String VIEW = "hasAuthority('METAL_VIEW')";
    private static final String MANAGE = "hasAuthority('METAL_MANAGE')";
    private static final String PUBLISH = "hasAuthority('METAL_RATE_PUBLISH')";

    private final MetalService metalService;
    private final MetalRateService rateService;

    @Operation(summary = "List metals")
    @GetMapping("/metals")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<MetalResponse>>> listMetals() {
        return ResponseEntity.ok(ApiResponse.ok(metalService.listMetals()));
    }

    @Operation(summary = "Get a metal")
    @GetMapping("/metals/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<MetalResponse>> getMetal(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(metalService.getMetal(id)));
    }

    @Operation(summary = "Create a metal")
    @PostMapping("/metals")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<MetalResponse>> createMetal(@Valid @RequestBody MetalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(metalService.createMetal(request)));
    }

    @Operation(summary = "Update a metal")
    @PutMapping("/metals/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<MetalResponse>> updateMetal(
            @PathVariable UUID id, @Valid @RequestBody MetalRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(metalService.updateMetal(id, request)));
    }

    @Operation(summary = "List the purities of a metal")
    @GetMapping("/metals/{metalId}/purities")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<PurityResponse>>> listPurities(@PathVariable UUID metalId) {
        return ResponseEntity.ok(ApiResponse.ok(metalService.listPurities(metalId)));
    }

    @Operation(summary = "Create a purity")
    @PostMapping("/purities")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PurityResponse>> createPurity(
            @Valid @RequestBody PurityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(metalService.createPurity(request)));
    }

    @Operation(summary = "Update a purity")
    @PutMapping("/purities/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PurityResponse>> updatePurity(
            @PathVariable UUID id, @Valid @RequestBody PurityRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(metalService.updatePurity(id, request)));
    }

    @Operation(summary = "Publish a daily metal rate")
    @PostMapping("/metal-rates")
    @PreAuthorize(PUBLISH)
    public ResponseEntity<ApiResponse<MetalRateResponse>> publishRate(
            @Valid @RequestBody PublishRateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(rateService.publish(request)));
    }

    @Operation(summary = "Search published rates")
    @GetMapping("/metal-rates")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<MetalRateResponse>>> searchRates(
            @RequestParam(required = false) UUID metalId,
            @RequestParam(required = false) UUID purityId,
            @RequestParam(required = false) RateType rateType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                rateService.search(metalId, purityId, rateType, from, to, pageable)));
    }

    @Operation(summary = "Get the rate in force for a metal/purity")
    @GetMapping("/metal-rates/current")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<MetalRateResponse>> currentRate(
            @RequestParam UUID metalId,
            @RequestParam UUID purityId,
            @RequestParam(defaultValue = "SELLING") RateType rateType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate onDate,
            @RequestParam(required = false) UUID branchId) {
        return ResponseEntity.ok(ApiResponse.ok(
                rateService.currentRate(metalId, purityId, rateType, onDate, branchId)));
    }
}
