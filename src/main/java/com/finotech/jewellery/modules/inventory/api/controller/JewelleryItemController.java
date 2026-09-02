package com.finotech.jewellery.modules.inventory.api.controller;

import com.finotech.jewellery.modules.inventory.api.request.LinkImageRequest;
import com.finotech.jewellery.modules.inventory.api.request.AssignBinRequest;
import com.finotech.jewellery.modules.inventory.api.request.ChangeStatusRequest;
import com.finotech.jewellery.modules.inventory.api.request.CreateItemRequest;
import com.finotech.jewellery.modules.inventory.api.request.ReserveItemRequest;
import com.finotech.jewellery.modules.inventory.api.request.TagItemRequest;
import com.finotech.jewellery.modules.inventory.api.request.UpdateItemRequest;
import com.finotech.jewellery.modules.inventory.api.response.ItemImageResponse;
import com.finotech.jewellery.modules.inventory.api.response.ItemPassportResponse;
import com.finotech.jewellery.modules.inventory.api.response.JewelleryItemResponse;
import com.finotech.jewellery.modules.inventory.application.service.JewelleryItemService;
import com.finotech.jewellery.modules.inventory.domain.enums.ItemStatus;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Jewellery Items")
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class JewelleryItemController {

    private static final String VIEW = "hasAuthority('INVENTORY_VIEW')";
    private static final String CREATE = "hasAuthority('INVENTORY_CREATE')";
    private static final String RESERVE = "hasAuthority('INVENTORY_RESERVE')";
    private static final String ADJUST = "hasAuthority('INVENTORY_ADJUST')";

    private final JewelleryItemService itemService;

    @Operation(summary = "Search serialized jewellery items")
    @GetMapping("/items")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<JewelleryItemResponse>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) ItemStatus status,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID metalId,
            @RequestParam(required = false) UUID purityId,
            @RequestParam(required = false) UUID binId,
            @PageableDefault(size = 20, sort = "itemCode") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.search(search, productId, status,
                locationId, branchId, metalId, purityId, binId, pageable)));
    }

    @Operation(summary = "Get an item")
    @GetMapping("/items/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<JewelleryItemResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.get(id)));
    }

    @Operation(summary = "Resolve an item by RFID, QR, barcode or item code")
    @GetMapping("/items/by-tag")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<JewelleryItemResponse>> byTag(@RequestParam String tag) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.findByTag(tag)));
    }

    @Operation(summary = "Digital passport: identity, stones and full lifecycle history")
    @GetMapping("/items/{id}/passport")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<ItemPassportResponse>> passport(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.passport(id)));
    }

    @Operation(summary = "Create a serialized item")
    @PostMapping("/items")
    @PreAuthorize(CREATE)
    public ResponseEntity<ApiResponse<JewelleryItemResponse>> create(
            @Valid @RequestBody CreateItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(itemService.create(request)));
    }

    @Operation(summary = "Update an item's physical and cost details")
    @PutMapping("/items/{id}")
    @PreAuthorize(CREATE)
    public ResponseEntity<ApiResponse<JewelleryItemResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.update(id, request)));
    }

    @Operation(summary = "Attach RFID, QR and barcode tags")
    @PostMapping("/items/{id}/tags")
    @PreAuthorize(CREATE)
    public ResponseEntity<ApiResponse<JewelleryItemResponse>> tag(
            @PathVariable UUID id, @Valid @RequestBody TagItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.tag(id, request)));
    }

    @Operation(summary = "Pass quality check and release the item into sellable stock")
    @PostMapping("/items/{id}/release")
    @PreAuthorize(CREATE)
    public ResponseEntity<ApiResponse<JewelleryItemResponse>> release(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.approveForStock(id)));
    }

    @Operation(summary = "Put the item in a storage bin, or take it out of one",
            description = "The bin must belong to the item's current location. "
                    + "Send a null binId to clear the assignment.")
    @PostMapping("/items/{id}/bin")
    @PreAuthorize(ADJUST)
    public ResponseEntity<ApiResponse<JewelleryItemResponse>> assignBin(
            @PathVariable UUID id, @RequestBody(required = false) AssignBinRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.assignBin(id, request)));
    }

    @Operation(summary = "List the item's photographs")
    @GetMapping("/items/{id}/images")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<ItemImageResponse>>> images(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.images(id)));
    }

    @Operation(summary = "Link an uploaded photograph to the item",
            description = "Upload the file to POST /api/v1/files first, then send the "
                    + "storage key it returns.")
    @PostMapping("/items/{id}/images")
    @PreAuthorize(CREATE)
    public ResponseEntity<ApiResponse<ItemImageResponse>> addImage(
            @PathVariable UUID id, @Valid @RequestBody LinkImageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(itemService.addImage(id, request)));
    }

    @Operation(summary = "Unlink a photograph from the item",
            description = "The stored file itself is kept; only the link is removed.")
    @DeleteMapping("/items/{id}/images/{imageId}")
    @PreAuthorize(CREATE)
    public ResponseEntity<ApiResponse<Void>> removeImage(
            @PathVariable UUID id, @PathVariable UUID imageId) {
        itemService.removeImage(id, imageId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "Reserve an item for a customer")
    @PostMapping("/reservations")
    @PreAuthorize(RESERVE)
    public ResponseEntity<ApiResponse<JewelleryItemResponse>> reserve(
            @Valid @RequestBody ReserveItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(itemService.reserve(request)));
    }

    @Operation(summary = "Release a reservation")
    @PostMapping("/items/{id}/release-reservation")
    @PreAuthorize(RESERVE)
    public ResponseEntity<ApiResponse<Void>> releaseReservation(@PathVariable UUID id) {
        itemService.releaseReservation(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Reservation released"));
    }

    @Operation(summary = "Manually correct an item's status")
    @PostMapping("/items/{id}/status")
    @PreAuthorize(ADJUST)
    public ResponseEntity<ApiResponse<JewelleryItemResponse>> changeStatus(
            @PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.changeStatus(id, request)));
    }
}
