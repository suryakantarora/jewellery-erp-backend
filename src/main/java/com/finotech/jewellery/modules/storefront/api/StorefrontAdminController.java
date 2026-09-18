package com.finotech.jewellery.modules.storefront.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finotech.jewellery.modules.organization.application.CompanyScope;
import com.finotech.jewellery.modules.storefront.application.OnlineOrderService;
import com.finotech.jewellery.modules.storefront.application.StorefrontAdminService;
import com.finotech.jewellery.modules.storefront.application.Tenant;
import com.finotech.jewellery.modules.storefront.application.TenantService;
import com.finotech.jewellery.shared.common.ApiResponse;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff side of the customer app: the shop's key and branding, its editorial
 * content, retail attributes of products, reviews, and the online orders.
 */
@Tag(name = "Storefront (staff)")
@RestController
@RequestMapping("/api/v1/storefront-admin")
@RequiredArgsConstructor
public class StorefrontAdminController {

    private final TenantService tenants;
    private final StorefrontAdminService admin;
    private final OnlineOrderService orders;
    private final CompanyScope companyScope;

    public record TenantRequest(UUID companyId, String tenantKey, JsonNode config, Boolean active) {
    }

    public record TenantResponse(UUID companyId, String tenantKey, JsonNode storedConfig,
                                 JsonNode effectiveConfig) {
    }

    @Operation(summary = "The shop's tenant key and branding document")
    @GetMapping("/tenant")
    @PreAuthorize("hasAuthority('STOREFRONT_VIEW')")
    public ApiResponse<TenantResponse> tenant(@RequestParam(required = false) UUID companyId) {
        UUID company = companyScope.resolveForCreate(companyId);
        Tenant tenant = tenants.ofCompany(company)
                .orElseThrow(() -> new NotFoundException("This company has no customer app key yet"));
        return ApiResponse.ok(new TenantResponse(company, tenant.key(),
                tenants.storedConfig(company).orElse(null), tenant.config()));
    }

    @Operation(summary = "Set the tenant key and branding",
            description = "config is the app's TenantConfig document; anything omitted falls "
                    + "back to the company record and the app's default palette.")
    @PutMapping("/tenant")
    @PreAuthorize("hasAuthority('STOREFRONT_MANAGE')")
    public ApiResponse<TenantResponse> saveTenant(@RequestBody TenantRequest request) {
        UUID company = companyScope.resolveForCreate(request.companyId());
        Tenant tenant = tenants.save(company, request.tenantKey(), request.config(),
                request.active() == null || request.active());
        return ApiResponse.ok(new TenantResponse(company, tenant.key(),
                tenants.storedConfig(company).orElse(null), tenant.config()));
    }

    @GetMapping("/content/{kind}")
    @PreAuthorize("hasAuthority('STOREFRONT_VIEW')")
    public ApiResponse<JsonNode> content(@PathVariable String kind,
                                         @RequestParam(required = false) UUID companyId) {
        return ApiResponse.ok(tenants.content(companyScope.resolveForCreate(companyId), kind));
    }

    @Operation(summary = "Replace a content document",
            description = "banners, policies, stores, offers, brands, trending, stories are "
                    + "arrays; about and categories (keyed by category code) are objects.")
    @PutMapping("/content/{kind}")
    @PreAuthorize("hasAuthority('STOREFRONT_MANAGE')")
    public ApiResponse<JsonNode> putContent(@PathVariable String kind,
                                            @RequestParam(required = false) UUID companyId,
                                            @RequestBody JsonNode payload) {
        UUID company = companyScope.resolveForCreate(companyId);
        tenants.putContent(company, kind, payload, SecurityUtils.currentUsername().orElse(null));
        return ApiResponse.ok(tenants.content(company, kind));
    }

    @Operation(summary = "Retail attributes of a product",
            description = "sizes, sizeKind, audience, stone, tags, brand, collection, "
                    + "originalPrice, isFeatured, isTrending, isBestSeller, deliveryDays…")
    @PutMapping("/products/{productId}/retail")
    @PreAuthorize("hasAuthority('STOREFRONT_MANAGE')")
    public ApiResponse<JsonNode> putRetail(@PathVariable UUID productId,
                                           @RequestBody JsonNode attributes) {
        return ApiResponse.ok(admin.putRetail(productId, attributes));
    }

    public record ReviewRequest(UUID productId, String author, String avatar, double rating,
                                String body, boolean verified, boolean featured) {
    }

    @PostMapping("/reviews")
    @PreAuthorize("hasAuthority('STOREFRONT_MANAGE')")
    public ApiResponse<UUID> addReview(@RequestBody ReviewRequest request) {
        return ApiResponse.ok(admin.addReview(request));
    }

    public record BroadcastRequest(UUID companyId, String kind, String title, String body,
                                   String link) {
    }

    @Operation(summary = "Post a notification to every customer's inbox")
    @PostMapping("/notifications")
    @PreAuthorize("hasAuthority('STOREFRONT_MANAGE')")
    public ApiResponse<Void> broadcast(@RequestBody BroadcastRequest request) {
        admin.broadcast(companyScope.resolveForCreate(request.companyId()), request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('STOREFRONT_VIEW')")
    public ApiResponse<List<ObjectNode>> orders(@RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "50") int limit,
                                                @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.ok(orders.forStaff(companyScope.currentOrNull(), status, limit, offset));
    }

    public record OrderStatusRequest(String status, String courier, String trackingNumber,
                                     JsonNode eta) {
    }

    @Operation(summary = "Move an online order along; the customer is notified")
    @PatchMapping("/orders/{number}/status")
    @PreAuthorize("hasAuthority('STOREFRONT_MANAGE')")
    public ApiResponse<ObjectNode> updateOrder(@PathVariable String number,
                                               @RequestBody OrderStatusRequest request) {
        return ApiResponse.ok(orders.updateStatus(companyScope.currentOrNull(), number,
                request.status(), request.courier(), request.trackingNumber(), request.eta(),
                SecurityUtils.currentUsername().orElse(null)));
    }
}
