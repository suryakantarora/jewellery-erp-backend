package com.finotech.jewellery.modules.product.api.controller;

import com.finotech.jewellery.modules.product.api.request.CategoryRequest;
import com.finotech.jewellery.modules.product.api.request.ProductTypeRequest;
import com.finotech.jewellery.modules.product.api.request.SimpleMasterRequest;
import com.finotech.jewellery.modules.product.api.request.SizeRequest;
import com.finotech.jewellery.modules.product.api.response.MasterResponse;
import com.finotech.jewellery.modules.product.api.response.SizeResponse;
import com.finotech.jewellery.modules.product.application.service.ProductMasterService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Product Master Data")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProductMasterController {

    private static final String VIEW = "hasAuthority('PRODUCT_VIEW')";
    private static final String CREATE = "hasAuthority('PRODUCT_CREATE')";
    private static final String UPDATE = "hasAuthority('PRODUCT_UPDATE')";

    private final ProductMasterService masterService;

    @Operation(summary = "List product categories")
    @GetMapping("/product-categories")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<MasterResponse>>> listCategories() {
        return ResponseEntity.ok(ApiResponse.ok(masterService.listCategories()));
    }

    @Operation(summary = "Create a product category")
    @PostMapping("/product-categories")
    @PreAuthorize(CREATE)
    public ResponseEntity<ApiResponse<MasterResponse>> createCategory(
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(masterService.createCategory(request)));
    }

    @Operation(summary = "Update a product category")
    @PutMapping("/product-categories/{id}")
    @PreAuthorize(UPDATE)
    public ResponseEntity<ApiResponse<MasterResponse>> updateCategory(
            @PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(masterService.updateCategory(id, request)));
    }

    @Operation(summary = "List product types")
    @GetMapping("/product-types")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<MasterResponse>>> listProductTypes() {
        return ResponseEntity.ok(ApiResponse.ok(masterService.listProductTypes()));
    }

    @Operation(summary = "Create a product type")
    @PostMapping("/product-types")
    @PreAuthorize(CREATE)
    public ResponseEntity<ApiResponse<MasterResponse>> createProductType(
            @Valid @RequestBody ProductTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(masterService.createProductType(request)));
    }

    @Operation(summary = "Update a product type")
    @PutMapping("/product-types/{id}")
    @PreAuthorize(UPDATE)
    public ResponseEntity<ApiResponse<MasterResponse>> updateProductType(
            @PathVariable UUID id, @Valid @RequestBody ProductTypeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(masterService.updateProductType(id, request)));
    }

    @Operation(summary = "List brands")
    @GetMapping("/brands")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<MasterResponse>>> listBrands() {
        return ResponseEntity.ok(ApiResponse.ok(masterService.listBrands()));
    }

    @Operation(summary = "Create a brand")
    @PostMapping("/brands")
    @PreAuthorize(CREATE)
    public ResponseEntity<ApiResponse<MasterResponse>> createBrand(
            @Valid @RequestBody SimpleMasterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(masterService.createBrand(request)));
    }

    @Operation(summary = "List collections")
    @GetMapping("/collections")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<MasterResponse>>> listCollections() {
        return ResponseEntity.ok(ApiResponse.ok(masterService.listCollections()));
    }

    @Operation(summary = "Create a collection")
    @PostMapping("/collections")
    @PreAuthorize(CREATE)
    public ResponseEntity<ApiResponse<MasterResponse>> createCollection(
            @Valid @RequestBody SimpleMasterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(masterService.createCollection(request)));
    }

    @Operation(summary = "List the sizes of a product type")
    @GetMapping("/sizes")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<SizeResponse>>> listSizes(@RequestParam UUID productTypeId) {
        return ResponseEntity.ok(ApiResponse.ok(masterService.listSizes(productTypeId)));
    }

    @Operation(summary = "Create a size")
    @PostMapping("/sizes")
    @PreAuthorize(CREATE)
    public ResponseEntity<ApiResponse<SizeResponse>> createSize(@Valid @RequestBody SizeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(masterService.createSize(request)));
    }
}
