package com.finotech.jewellery.modules.product.api.controller;

import com.finotech.jewellery.modules.product.api.request.DesignRequest;
import com.finotech.jewellery.modules.product.api.request.ProductRequest;
import com.finotech.jewellery.modules.product.api.response.DesignResponse;
import com.finotech.jewellery.modules.product.api.response.ProductResponse;
import com.finotech.jewellery.modules.product.application.service.ProductService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Products & Designs")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProductController {

    private static final String VIEW = "hasAuthority('PRODUCT_VIEW')";
    private static final String CREATE = "hasAuthority('PRODUCT_CREATE')";
    private static final String UPDATE = "hasAuthority('PRODUCT_UPDATE')";

    private final ProductService productService;

    @Operation(summary = "Search designs")
    @GetMapping("/designs")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<DesignResponse>>> searchDesigns(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID collectionId,
            @RequestParam(required = false) UUID productTypeId,
            @PageableDefault(size = 20, sort = "designCode") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.searchDesigns(search, collectionId, productTypeId, pageable)));
    }

    @Operation(summary = "Get a design")
    @GetMapping("/designs/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<DesignResponse>> getDesign(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getDesign(id)));
    }

    @Operation(summary = "Create a design")
    @PostMapping("/designs")
    @PreAuthorize(CREATE)
    public ResponseEntity<ApiResponse<DesignResponse>> createDesign(
            @Valid @RequestBody DesignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(productService.createDesign(request)));
    }

    @Operation(summary = "Update a design")
    @PutMapping("/designs/{id}")
    @PreAuthorize(UPDATE)
    public ResponseEntity<ApiResponse<DesignResponse>> updateDesign(
            @PathVariable UUID id, @Valid @RequestBody DesignRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(productService.updateDesign(id, request)));
    }

    @Operation(summary = "Search products")
    @GetMapping("/products")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> searchProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID productTypeId,
            @RequestParam(required = false) UUID brandId,
            @RequestParam(required = false) UUID collectionId,
            @PageableDefault(size = 20, sort = "sku") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(productService.searchProducts(
                search, categoryId, productTypeId, brandId, collectionId, pageable)));
    }

    @Operation(summary = "Get a product")
    @GetMapping("/products/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getProduct(id)));
    }

    @Operation(summary = "Create a product")
    @PostMapping("/products")
    @PreAuthorize(CREATE)
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(productService.createProduct(request)));
    }

    @Operation(summary = "Update a product")
    @PutMapping("/products/{id}")
    @PreAuthorize(UPDATE)
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(productService.updateProduct(id, request)));
    }

    @Operation(summary = "Deactivate a product")
    @DeleteMapping("/products/{id}")
    @PreAuthorize(UPDATE)
    public ResponseEntity<ApiResponse<Void>> deactivateProduct(@PathVariable UUID id) {
        productService.deactivateProduct(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Product deactivated"));
    }
}
