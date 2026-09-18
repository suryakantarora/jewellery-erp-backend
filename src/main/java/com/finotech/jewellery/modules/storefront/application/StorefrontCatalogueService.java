package com.finotech.jewellery.modules.storefront.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finotech.jewellery.modules.pricing.application.PricingCalculator;
import com.finotech.jewellery.modules.storefront.infrastructure.StorefrontCatalogueQuery;
import com.finotech.jewellery.modules.storefront.infrastructure.StorefrontCatalogueQuery.ItemRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The catalogue as the customer app reads it: the whole of a company's
 * sellable stock, priced live, in the shape of the app's {@code CatalogueItem}.
 *
 * <p>The app asks for the full list and sorts and filters on the device — a
 * jeweller's catalogue is hundreds of pieces, and the app's facets (price
 * band, rating, tags) are mostly over values that only exist after pricing, so
 * a server-side filter would have to price everything anyway. What costs here
 * is the pricing engine, one call per piece, so the priced list is reused for
 * a short window per company.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorefrontCatalogueService {

    /** A piece counts as a new arrival for this long unless merchandising says otherwise. */
    private static final Duration NEW_FOR = Duration.ofDays(30);

    private final StorefrontCatalogueQuery query;
    private final PricingCalculator pricing;
    private final TenantService tenants;
    private final StorefrontProperties properties;
    private final ObjectMapper objectMapper;

    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(Instant loadedAt, List<Entry> entries) {
    }

    /** One priced piece: the JSON the app receives, and what order placement needs. */
    public record Entry(UUID id, UUID productId, String itemCode, String name, String image,
                        BigDecimal price, String currency, ObjectNode json) {
    }

    public List<Entry> catalogue(UUID companyId) {
        Cached cached = cache.get(companyId);
        Duration ttl = Duration.ofSeconds(properties.catalogueCacheSeconds());
        if (cached != null && cached.loadedAt().plus(ttl).isAfter(Instant.now())) {
            return cached.entries();
        }
        List<Entry> entries = load(companyId);
        cache.put(companyId, new Cached(Instant.now(), entries));
        return entries;
    }

    public List<ObjectNode> items(UUID companyId) {
        return catalogue(companyId).stream().map(Entry::json).toList();
    }

    public Optional<Entry> item(UUID companyId, UUID itemId) {
        return catalogue(companyId).stream().filter(e -> e.id().equals(itemId)).findFirst();
    }

    /** Called when stock the storefront shows has changed hands (an order, a cancellation). */
    public void evict(UUID companyId) {
        cache.remove(companyId);
    }

    /**
     * ERP categories, dressed with whatever the {@code categories} content
     * document says about them (translations, artwork, featured), keyed by code.
     */
    public List<ObjectNode> categories(UUID companyId) {
        Map<String, Integer> counts = new HashMap<>();
        catalogue(companyId).forEach(e ->
                counts.merge(e.json().path("categoryId").asText(), 1, Integer::sum));
        JsonNode dressing = tenants.content(companyId, "categories");

        List<ObjectNode> result = new ArrayList<>();
        for (StorefrontCatalogueQuery.CategoryRow row : query.categories(companyId)) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("id", row.code());
            node.put("name", row.name());
            node.put("description", row.description() == null ? "" : row.description());
            JsonNode extra = dressing.get(row.code());
            if (extra != null && extra.isObject()) {
                node.setAll((ObjectNode) extra);
            }
            node.put("id", row.code());
            node.put("productCount", counts.getOrDefault(row.code(), 0));
            result.add(node);
        }
        return result;
    }

    private List<Entry> load(UUID companyId) {
        List<ItemRow> rows = query.sellable(companyId);
        Map<UUID, List<String>> itemImages = query.itemImages(companyId);
        Map<UUID, List<String>> productImages = query.productImages(companyId);
        Map<UUID, List<String>> designImages = query.designImages(companyId);
        Map<UUID, StorefrontCatalogueQuery.ReviewStats> reviews = query.reviewStats(companyId);

        List<Entry> entries = new ArrayList<>(rows.size());
        for (ItemRow row : rows) {
            BigDecimal price;
            String currency = row.currency();
            try {
                // List price: no customer, so no tier discount, as in the staff catalogue.
                PricingCalculator.PriceBreakdown breakdown = pricing.calculate(
                        new PricingCalculator.PriceRequest(row.id(), null, row.branchId(),
                                null, null, false, false));
                price = breakdown.finalPrice();
                currency = breakdown.currency();
            } catch (RuntimeException ex) {
                // The staff catalogue shows an unpriced piece and says so. A
                // shop window cannot: there is nothing to add to a bag.
                log.debug("Storefront skips {}: {}", row.itemCode(), ex.getMessage());
                continue;
            }
            if (price == null || price.signum() <= 0) {
                continue;
            }
            entries.add(toEntry(row, price, currency, itemImages, productImages, designImages,
                    reviews.get(row.productId())));
        }
        return List.copyOf(entries);
    }

    private Entry toEntry(ItemRow row, BigDecimal price, String currency,
                          Map<UUID, List<String>> itemImages,
                          Map<UUID, List<String>> productImages,
                          Map<UUID, List<String>> designImages,
                          StorefrontCatalogueQuery.ReviewStats stats) {
        List<String> own = itemImages.getOrDefault(row.id(), List.of());
        List<String> design = row.designId() == null
                ? List.of() : designImages.getOrDefault(row.designId(), List.of());
        List<String> product = productImages.getOrDefault(row.productId(), List.of());
        List<String> artwork = design.isEmpty() ? product : design;

        // Insertion-ordered and de-duplicated: own photographs, then artwork.
        Map<String, Boolean> gallery = new LinkedHashMap<>();
        own.forEach(k -> gallery.put(k, true));
        artwork.forEach(k -> gallery.put(k, true));

        ObjectNode json = objectMapper.createObjectNode();
        json.put("id", row.id().toString());
        json.put("itemCode", row.itemCode());
        json.put("productName", row.productName());
        json.put("categoryId", row.categoryCode() == null ? "" : row.categoryCode());
        json.put("categoryName", row.categoryName() == null ? "" : row.categoryName());
        json.put("designName", row.designName() == null ? "" : row.designName());
        json.put("metalName", row.metalName() == null ? "" : row.metalName());
        json.put("purityCode", row.purityCode() == null ? "" : row.purityCode());
        json.put("purityName", row.purityName() == null ? "" : row.purityName());
        json.put("grossWeight", row.grossWeight());
        json.put("netMetalWeight", row.netMetalWeight());
        json.put("stoneCount", row.stoneCount());
        json.put("totalCarat", row.totalCarat() == null ? BigDecimal.ZERO : row.totalCarat());
        if (row.hallmarkNumber() != null) {
            json.put("hallmarkNumber", row.hallmarkNumber());
        }
        json.put("price", price);
        json.put("currency", currency);
        if (!own.isEmpty()) {
            json.put("primaryImageKey", own.get(0));
        }
        if (!artwork.isEmpty()) {
            json.put("fallbackImageKey", artwork.get(0));
        }
        ArrayNode images = json.putArray("images");
        gallery.keySet().forEach(images::add);
        json.set("retail", retail(row, price, stats));

        String hero = gallery.isEmpty() ? null : gallery.keySet().iterator().next();
        return new Entry(row.id(), row.productId(), row.itemCode(), row.productName(), hero,
                price, currency, json);
    }

    private ObjectNode retail(ItemRow row, BigDecimal price,
                              StorefrontCatalogueQuery.ReviewStats stats) {
        ObjectNode retail = objectMapper.createObjectNode();
        retail.put("isNew", row.createdAt().isAfter(Instant.now().minus(NEW_FOR)));
        retail.put("stone", row.stoneCount() > 0 ? "Gemstone" : "None");
        retail.put("certified", row.hallmarkNumber() != null);
        if (row.description() != null) {
            retail.put("description", row.description());
        }
        if (row.retailJson() != null) {
            try {
                JsonNode stored = objectMapper.readTree(row.retailJson());
                if (stored.isObject()) {
                    retail.setAll((ObjectNode) stored);
                }
            } catch (JsonProcessingException ex) {
                log.warn("Ignoring unreadable retail attributes of product {}", row.productId());
            }
        }

        // Computed, never stored: a stored rating would drift from the reviews,
        // and a stored discount from the live price.
        retail.put("rating", stats == null ? 0
                : BigDecimal.valueOf(stats.rating()).setScale(1, RoundingMode.HALF_UP).doubleValue());
        retail.put("reviewCount", stats == null ? 0 : stats.count());
        retail.put("inStock", true);
        JsonNode original = retail.get("originalPrice");
        if (original != null && original.isNumber() && original.decimalValue().compareTo(price) > 0) {
            BigDecimal was = original.decimalValue();
            retail.put("discountPercent", was.subtract(price)
                    .multiply(BigDecimal.valueOf(100)).divide(was, 0, RoundingMode.HALF_UP).intValue());
        } else {
            retail.remove("originalPrice");
            retail.remove("discountPercent");
        }
        return retail;
    }
}
