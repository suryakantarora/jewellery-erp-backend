package com.finotech.jewellery.modules.warehouse.api.controller;

import com.finotech.jewellery.modules.warehouse.api.request.WarehouseRequests;
import com.finotech.jewellery.modules.warehouse.api.response.WarehouseResponses.BinResponse;
import com.finotech.jewellery.modules.warehouse.api.response.WarehouseResponses.StockCountResponse;
import com.finotech.jewellery.modules.warehouse.application.service.WarehouseService;
import com.finotech.jewellery.modules.warehouse.domain.enums.StockCountStatus;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Warehouse & Vault")
@RestController
@RequestMapping("/api/v1/warehouse")
@RequiredArgsConstructor
public class WarehouseController {

    private static final String VIEW = "hasAuthority('WAREHOUSE_VIEW')";
    private static final String MANAGE = "hasAuthority('WAREHOUSE_MANAGE')";
    private static final String COUNT = "hasAuthority('STOCK_COUNT_PERFORM')";
    private static final String APPROVE = "hasAuthority('STOCK_COUNT_APPROVE')";

    private final WarehouseService warehouseService;

    // ---------- bins ----------

    @Operation(summary = "List the storage bins of a location")
    @GetMapping("/bins")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<BinResponse>>> listBins(@RequestParam UUID locationId) {
        return ResponseEntity.ok(ApiResponse.ok(warehouseService.listBins(locationId)));
    }

    @Operation(summary = "Create a storage bin (zone, shelf, tray, safe)")
    @PostMapping("/bins")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<BinResponse>> createBin(
            @Valid @RequestBody WarehouseRequests.BinRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(warehouseService.createBin(request)));
    }

    @Operation(summary = "Deactivate a storage bin")
    @DeleteMapping("/bins/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<BinResponse>> deactivateBin(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(warehouseService.deactivateBin(id)));
    }

    // ---------- stock counts ----------

    @Operation(summary = "Search stock verifications")
    @GetMapping("/stock-counts")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<StockCountResponse>>> searchCounts(
            @RequestParam(required = false) StockCountStatus status,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID branchId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                warehouseService.searchCounts(status, locationId, branchId, pageable)));
    }

    @Operation(summary = "Get a stock count with its full sheet")
    @GetMapping("/stock-counts/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<StockCountResponse>> getCount(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(warehouseService.getCount(id)));
    }

    @Operation(summary = "Open a count and snapshot the expected stock",
            description = "Only one count may be open per location at a time.")
    @PostMapping("/stock-counts")
    @PreAuthorize(COUNT)
    public ResponseEntity<ApiResponse<StockCountResponse>> startCount(
            @Valid @RequestBody WarehouseRequests.StartCountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(warehouseService.startCount(request)));
    }

    @Operation(summary = "Submit what was physically found",
            description = "Anything expected but absent is recorded as missing; anything "
                    + "found that was not expected is added as a variance line.")
    @PostMapping("/stock-counts/{id}/submit")
    @PreAuthorize(COUNT)
    public ResponseEntity<ApiResponse<StockCountResponse>> submitCount(
            @PathVariable UUID id,
            @Valid @RequestBody WarehouseRequests.SubmitCountRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(warehouseService.submitCount(id, request)));
    }

    @Operation(summary = "Review a count",
            description = "A vault count, or any count with a variance, needs two different "
                    + "approvers before it closes. Stock is never adjusted automatically.")
    @PostMapping("/stock-counts/{id}/approve")
    @PreAuthorize(APPROVE)
    public ResponseEntity<ApiResponse<StockCountResponse>> approveCount(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(warehouseService.approveCount(id)));
    }

    @Operation(summary = "Cancel a stock count")
    @PostMapping("/stock-counts/{id}/cancel")
    @PreAuthorize(APPROVE)
    public ResponseEntity<ApiResponse<StockCountResponse>> cancelCount(
            @PathVariable UUID id, @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.ok(warehouseService.cancelCount(id, reason)));
    }
}
