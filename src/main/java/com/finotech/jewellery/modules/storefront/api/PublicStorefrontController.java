package com.finotech.jewellery.modules.storefront.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finotech.jewellery.modules.storefront.application.CustomerAccountService;
import com.finotech.jewellery.modules.storefront.application.StorefrontCatalogueService;
import com.finotech.jewellery.modules.storefront.application.Tenant;
import com.finotech.jewellery.modules.storefront.application.TenantService;
import com.finotech.jewellery.modules.storefront.infrastructure.StorefrontCatalogueQuery;
import com.finotech.jewellery.modules.storefront.infrastructure.StorefrontContentQuery;
import com.finotech.jewellery.shared.common.ApiResponse;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.security.AuthenticatedCustomer;
import com.finotech.jewellery.shared.storage.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the customer app may read without signing in: branding, catalogue,
 * editorial content and rates of the shop named by {@code X-Tenant-Key}.
 *
 * <p>Anonymous by design, so nothing here may ever carry cost, supplier,
 * location or another customer's data; the read models behind it cannot reach
 * those columns.
 */
@Tag(name = "Storefront (public)")
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicStorefrontController {

    static final String TENANT_HEADER = "X-Tenant-Key";

    private static final Map<String, MediaType> IMAGE_TYPES = Map.of(
            "jpg", MediaType.IMAGE_JPEG, "jpeg", MediaType.IMAGE_JPEG,
            "png", MediaType.IMAGE_PNG, "webp", MediaType.parseMediaType("image/webp"));

    private final TenantService tenants;
    private final StorefrontCatalogueService catalogue;
    private final StorefrontCatalogueQuery catalogueQuery;
    private final StorefrontContentQuery contentQuery;
    private final CustomerAccountService accounts;
    private final FileStorageService storage;
    private final ObjectMapper objectMapper;

    @Operation(summary = "Branding and settings of a shop")
    @GetMapping("/tenant/{key}")
    public ApiResponse<JsonNode> tenant(@PathVariable String key) {
        return ApiResponse.ok(tenants.require(key).config());
    }

    @Operation(summary = "The shop's whole sellable catalogue, priced live",
            description = "The app sorts and filters on the device; see StorefrontCatalogueService.")
    @GetMapping("/catalogue/items")
    public ApiResponse<List<ObjectNode>> items(@RequestHeader(value = TENANT_HEADER, required = false) String tenantKey) {
        return ApiResponse.ok(catalogue.items(tenants.require(tenantKey).companyId()));
    }

    @GetMapping("/catalogue/items/{id}")
    public ApiResponse<ObjectNode> item(@RequestHeader(value = TENANT_HEADER, required = false) String tenantKey,
                                        @PathVariable UUID id) {
        return ApiResponse.ok(catalogue.item(tenants.require(tenantKey).companyId(), id)
                .map(StorefrontCatalogueService.Entry::json)
                .orElseThrow(() -> new NotFoundException("This piece is no longer available")));
    }

    @GetMapping("/categories")
    public ApiResponse<List<ObjectNode>> categories(@RequestHeader(value = TENANT_HEADER, required = false) String tenantKey) {
        return ApiResponse.ok(catalogue.categories(tenants.require(tenantKey).companyId()));
    }

    @Operation(summary = "An editorial content document",
            description = "banners, policies, stores, offers, brands, trending, stories, about")
    @GetMapping("/content/{kind}")
    public ApiResponse<JsonNode> content(@RequestHeader(value = TENANT_HEADER, required = false) String tenantKey,
                                         @PathVariable String kind) {
        return ApiResponse.ok(tenants.content(tenants.require(tenantKey).companyId(), kind));
    }

    @Operation(summary = "Today's selling rate per purity")
    @GetMapping("/gold-rates")
    public ApiResponse<ObjectNode> goldRates(@RequestHeader(value = TENANT_HEADER, required = false) String tenantKey) {
        ObjectNode sheet = objectMapper.createObjectNode();
        ArrayNode rates = sheet.putArray("rates");
        Instant updatedAt = null;
        for (StorefrontContentQuery.RateRow row
                : contentQuery.sellingRates(tenants.require(tenantKey).companyId())) {
            ObjectNode rate = rates.addObject();
            rate.put("purityCode", row.purityCode());
            rate.put("label", row.purityCode());
            rate.put("metalName", row.metalName());
            rate.putObject("rates").put(row.currency(), row.ratePerUnit());
            if (updatedAt == null || row.publishedAt().isAfter(updatedAt)) {
                updatedAt = row.publishedAt();
            }
        }
        sheet.put("updatedAt", (updatedAt == null ? Instant.now() : updatedAt).toString());
        return ApiResponse.ok(sheet);
    }

    @Operation(summary = "Reviews of a piece's product, or the featured ones when no piece is named")
    @GetMapping("/reviews")
    public ApiResponse<List<ObjectNode>> reviews(@RequestHeader(value = TENANT_HEADER, required = false) String tenantKey,
                                                 @RequestParam(required = false) UUID productId) {
        UUID companyId = tenants.require(tenantKey).companyId();
        if (productId == null) {
            return ApiResponse.ok(contentQuery.featuredReviews(companyId).stream()
                    .map(r -> review(r, r.productId().toString())).toList());
        }
        // The app's "product" is a piece; reviews belong to the product it is
        // an instance of, and are echoed back under the id the app asked with.
        return ApiResponse.ok(catalogue.item(companyId, productId)
                .map(entry -> contentQuery.reviewsOfProduct(companyId, entry.productId()).stream()
                        .map(r -> review(r, productId.toString())).toList())
                .orElse(List.of()));
    }

    private ObjectNode review(StorefrontContentQuery.ReviewRow row, String productId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", row.id().toString());
        node.put("productId", productId);
        node.put("author", row.author());
        if (row.avatar() != null) {
            node.put("avatar", row.avatar());
        }
        node.put("rating", row.rating());
        node.put("date", row.createdAt().toString());
        node.put("body", row.body());
        node.put("verified", row.verified());
        return node;
    }

    public record FeedbackRequest(boolean happy, String topic, String message, String email,
                                  boolean followUp) {
    }

    public record RatingRequest(int stars, String comment) {
    }

    @Operation(summary = "Send feedback; guests may, a signed-in customer is recorded against it")
    @PostMapping("/feedback")
    public ApiResponse<Map<String, String>> feedback(@RequestHeader(value = TENANT_HEADER, required = false) String tenantKey,
                                                     @RequestBody FeedbackRequest request) {
        Tenant tenant = tenants.require(tenantKey);
        String ticket = accounts.submitFeedback(tenant.companyId(), customerOf(tenant),
                request.happy(), request.topic(), request.message(), request.email(),
                request.followUp());
        return ApiResponse.ok(Map.of("ticket", ticket));
    }

    @PostMapping("/ratings")
    public ApiResponse<Void> rate(@RequestHeader(value = TENANT_HEADER, required = false) String tenantKey,
                                  @RequestBody RatingRequest request) {
        Tenant tenant = tenants.require(tenantKey);
        accounts.rate(tenant.companyId(), customerOf(tenant), request.stars(), request.comment());
        return ApiResponse.ok(null);
    }

    /** The signed-in customer, when there is one and they belong to this shop. */
    private static UUID customerOf(Tenant tenant) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedCustomer customer
                && customer.companyId().equals(tenant.companyId()) ? customer.customerId() : null;
    }

    @Operation(summary = "Catalogue artwork by storage key",
            description = "Only photographs of products, designs and items, and files uploaded "
                    + "to the 'storefront' category. Anything else is a 404.")
    @GetMapping("/files")
    public ResponseEntity<InputStreamResource> file(@RequestParam String key) {
        int dot = key.lastIndexOf('.');
        MediaType type = dot < 0 ? null
                : IMAGE_TYPES.get(key.substring(dot + 1).toLowerCase(Locale.ROOT));
        if (type == null || !catalogueQuery.isPublicImage(key)) {
            throw new NotFoundException("No such image");
        }
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(storage.retrieve(key)));
    }
}
