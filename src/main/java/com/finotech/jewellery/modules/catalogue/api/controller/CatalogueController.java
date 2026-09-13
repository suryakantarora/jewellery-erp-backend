package com.finotech.jewellery.modules.catalogue.api.controller;

import com.finotech.jewellery.modules.catalogue.api.response.CatalogueItemResponse;
import com.finotech.jewellery.modules.catalogue.application.service.CatalogueService;
import com.finotech.jewellery.shared.common.ApiResponse;
import com.finotech.jewellery.shared.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stock as a customer should see it: photographs, names and a price.
 *
 * <p>Serves the "show the customer" mode in the staff app today. It is written
 * as the storefront read model it will need to become — names resolved, prices
 * live, no cost reachable — so a public variant is a change of authentication,
 * not a rewrite.
 */
@Tag(name = "Catalogue")
@RestController
@RequestMapping("/api/v1/catalogue")
@RequiredArgsConstructor
public class CatalogueController {

    private final CatalogueService catalogueService;

    @Operation(summary = "Browse sellable stock",
            description = "Available items only, priced live by the pricing engine. "
                    + "Carries no cost, supplier or location data of any kind.")
    @GetMapping("/items")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public ResponseEntity<ApiResponse<PageResponse<CatalogueItemResponse>>> browse(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID metalId,
            @RequestParam(required = false) UUID purityId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(catalogueService.search(
                branchId, categoryId, metalId, purityId, search, pageable)));
    }
}
