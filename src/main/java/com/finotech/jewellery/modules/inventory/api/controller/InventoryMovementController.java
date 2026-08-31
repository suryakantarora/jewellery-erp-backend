package com.finotech.jewellery.modules.inventory.api.controller;

import com.finotech.jewellery.modules.inventory.api.request.CreateMovementRequest;
import com.finotech.jewellery.modules.inventory.api.request.ReceiveMovementRequest;
import com.finotech.jewellery.modules.inventory.api.request.RejectMovementRequest;
import com.finotech.jewellery.modules.inventory.api.response.MovementResponse;
import com.finotech.jewellery.modules.inventory.application.service.InventoryMovementService;
import com.finotech.jewellery.modules.inventory.domain.enums.MovementStatus;
import com.finotech.jewellery.modules.inventory.domain.enums.MovementType;
import com.finotech.jewellery.shared.common.ApiResponse;
import com.finotech.jewellery.shared.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Inventory Movements")
@RestController
@RequestMapping("/api/v1/inventory/transfers")
@RequiredArgsConstructor
public class InventoryMovementController {

    private static final String VIEW = "hasAuthority('INVENTORY_VIEW')";
    private static final String TRANSFER = "hasAuthority('INVENTORY_TRANSFER')";
    private static final String APPROVE = "hasAuthority('INVENTORY_TRANSFER_APPROVE')";

    private final InventoryMovementService movementService;

    @Operation(summary = "Search movements")
    @GetMapping
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<MovementResponse>>> search(
            @RequestParam(required = false) MovementStatus status,
            @RequestParam(required = false) MovementType movementType,
            @RequestParam(required = false) UUID fromLocationId,
            @RequestParam(required = false) UUID toLocationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(movementService.search(status, movementType,
                fromLocationId, toLocationId, from, to, pageable)));
    }

    @Operation(summary = "Get a movement")
    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<MovementResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(movementService.get(id)));
    }

    @Operation(summary = "Raise a movement",
            description = "Send X-Idempotency-Key to make retries safe; a replayed key "
                    + "returns the movement already created.")
    @PostMapping
    @PreAuthorize(TRANSFER)
    public ResponseEntity<ApiResponse<MovementResponse>> create(
            @Valid @RequestBody CreateMovementRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(movementService.create(request, idempotencyKey)));
    }

    @Operation(summary = "Approve a movement")
    @PostMapping("/{id}/approve")
    @PreAuthorize(APPROVE)
    public ResponseEntity<ApiResponse<MovementResponse>> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(movementService.approve(id)));
    }

    @Operation(summary = "Reject a movement")
    @PostMapping("/{id}/reject")
    @PreAuthorize(APPROVE)
    public ResponseEntity<ApiResponse<MovementResponse>> reject(
            @PathVariable UUID id, @Valid @RequestBody RejectMovementRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(movementService.reject(id, request)));
    }

    @Operation(summary = "Dispatch a movement; items become IN_TRANSIT")
    @PostMapping("/{id}/dispatch")
    @PreAuthorize(TRANSFER)
    public ResponseEntity<ApiResponse<MovementResponse>> dispatch(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(movementService.dispatch(id)));
    }

    @Operation(summary = "Confirm receipt; items land at the destination")
    @PostMapping("/{id}/receive")
    @PreAuthorize(TRANSFER)
    public ResponseEntity<ApiResponse<MovementResponse>> receive(
            @PathVariable UUID id, @RequestBody(required = false) ReceiveMovementRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(movementService.receive(id, request)));
    }

    @Operation(summary = "Cancel a movement that has not been dispatched")
    @PostMapping("/{id}/cancel")
    @PreAuthorize(TRANSFER)
    public ResponseEntity<ApiResponse<MovementResponse>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(movementService.cancel(id)));
    }
}
