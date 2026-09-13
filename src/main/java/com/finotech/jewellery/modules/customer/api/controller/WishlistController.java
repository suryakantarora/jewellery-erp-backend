package com.finotech.jewellery.modules.customer.api.controller;

import com.finotech.jewellery.modules.customer.api.request.WishlistEntryRequest;
import com.finotech.jewellery.modules.customer.api.response.WishlistEntryResponse;
import com.finotech.jewellery.modules.customer.application.service.WishlistService;
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

@Tag(name = "Customers")
@RestController
@RequestMapping("/api/v1/customers/{customerId}/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private static final String VIEW = "hasAuthority('CUSTOMER_VIEW')";
    private static final String MANAGE = "hasAnyAuthority('CUSTOMER_MANAGE', 'SALE_CREATE')";

    private final WishlistService wishlistService;

    @Operation(summary = "A customer's wishlist, newest first, with live item details")
    @GetMapping
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<WishlistEntryResponse>>> list(
            @PathVariable UUID customerId) {
        return ResponseEntity.ok(ApiResponse.ok(wishlistService.list(customerId)));
    }

    @Operation(summary = "Add an item, product or design to the wishlist",
            description = "Adding a piece that is already on the list returns the existing "
                    + "entry with 200 rather than a conflict.")
    @PostMapping
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<WishlistEntryResponse>> add(
            @PathVariable UUID customerId, @Valid @RequestBody WishlistEntryRequest request) {
        WishlistService.AddResult result = wishlistService.add(customerId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.ok(result.entry()));
    }

    @Operation(summary = "Remove a wishlist entry")
    @DeleteMapping("/{entryId}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Void> remove(@PathVariable UUID customerId, @PathVariable UUID entryId) {
        wishlistService.remove(customerId, entryId);
        return ResponseEntity.noContent().build();
    }
}
