package com.finotech.jewellery.modules.gemstone.api.controller;

import com.finotech.jewellery.modules.gemstone.api.request.CertificateRequest;
import com.finotech.jewellery.modules.gemstone.api.request.GemstoneRequest;
import com.finotech.jewellery.modules.gemstone.api.response.CertificateResponse;
import com.finotech.jewellery.modules.gemstone.api.response.GemstoneResponse;
import com.finotech.jewellery.modules.gemstone.api.response.StoneResponse;
import com.finotech.jewellery.modules.gemstone.application.service.GemstoneService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Gemstones & Certificates")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GemstoneController {

    private static final String VIEW = "hasAuthority('GEMSTONE_VIEW')";
    private static final String MANAGE = "hasAuthority('GEMSTONE_MANAGE')";

    private final GemstoneService gemstoneService;

    @Operation(summary = "List gemstone types")
    @GetMapping("/gemstones")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<GemstoneResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(gemstoneService.listGemstones()));
    }

    @Operation(summary = "Create a gemstone type")
    @PostMapping("/gemstones")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<GemstoneResponse>> create(
            @Valid @RequestBody GemstoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(gemstoneService.createGemstone(request)));
    }

    @Operation(summary = "Update a gemstone type")
    @PutMapping("/gemstones/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<GemstoneResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody GemstoneRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(gemstoneService.updateGemstone(id, request)));
    }

    @Operation(summary = "Search stone certificates")
    @GetMapping("/stone-certificates")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<CertificateResponse>>> searchCertificates(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                gemstoneService.searchCertificates(search, pageable)));
    }

    @Operation(summary = "Register a stone certificate")
    @PostMapping("/stone-certificates")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CertificateResponse>> createCertificate(
            @Valid @RequestBody CertificateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(gemstoneService.createCertificate(request)));
    }

    @Operation(summary = "List the stones set in a jewellery item")
    @GetMapping("/jewellery-items/{itemId}/stones")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<StoneResponse>>> stonesOfItem(@PathVariable UUID itemId) {
        return ResponseEntity.ok(ApiResponse.ok(gemstoneService.stonesOfItem(itemId)));
    }
}
