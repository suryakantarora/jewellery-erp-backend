package com.finotech.jewellery.modules.storefront.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finotech.jewellery.modules.storefront.infrastructure.TenantStore;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Resolves the tenant key an app build carries to a company, and serves the
 * branding and editorial content that company has published.
 *
 * <p>A stored config is a partial document: whatever it omits is filled from
 * the company record, so a tenant that has only been given a key still yields a
 * config the app can launch with (its name, currency and contact details, on
 * the app's default palette).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    /** Content documents the app knows how to render. */
    public static final Set<String> CONTENT_KINDS = Set.of("banners", "categories", "policies",
            "stores", "offers", "brands", "trending", "stories", "about");

    /** Kinds that are a single object rather than a list. */
    private static final Set<String> OBJECT_KINDS = Set.of("about", "categories");

    private final TenantStore store;
    private final StorefrontProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public Tenant require(String tenantKey) {
        if (!StringUtils.hasText(tenantKey)) {
            throw new ValidationException("X-Tenant-Key header is required");
        }
        Optional<TenantStore.TenantRow> row = store.byKey(tenantKey.trim());
        if (row.isEmpty()) {
            row = bootstrap(tenantKey.trim());
        }
        TenantStore.TenantRow tenant = row.filter(TenantStore.TenantRow::active)
                .orElseThrow(() -> new NotFoundException("Unknown shop: " + tenantKey));
        return new Tenant(tenant.companyId(), tenant.tenantKey(),
                effectiveConfig(tenant.companyId(), tenant.tenantKey(), tenant.configJson()));
    }

    public Optional<Tenant> ofCompany(UUID companyId) {
        return store.byCompany(companyId).map(row -> new Tenant(row.companyId(), row.tenantKey(),
                effectiveConfig(row.companyId(), row.tenantKey(), row.configJson())));
    }

    /** The stored document as the administrator wrote it, without defaults. */
    public Optional<JsonNode> storedConfig(UUID companyId) {
        return store.byCompany(companyId).map(row -> parse(row.configJson()));
    }

    @Transactional
    public Tenant save(UUID companyId, String tenantKey, JsonNode config, boolean active) {
        String key = tenantKey == null ? "" : tenantKey.trim().toLowerCase();
        if (!key.matches("[a-z0-9][a-z0-9-]{1,58}")) {
            throw new ValidationException(
                    "Tenant key must be 2-59 characters of a-z, 0-9 and '-', starting with a letter or digit");
        }
        if (config != null && !config.isObject()) {
            throw new ValidationException("config must be a JSON object");
        }
        if (store.keyTakenByOther(key, companyId)) {
            throw new ConflictException("Tenant key already in use: " + key);
        }
        store.upsert(companyId, key, write(config == null ? objectMapper.createObjectNode() : config),
                active);
        return ofCompany(companyId).orElseThrow();
    }

    public JsonNode content(UUID companyId, String kind) {
        requireKind(kind);
        return store.content(companyId, kind).map(this::parse)
                .orElseGet(() -> OBJECT_KINDS.contains(kind)
                        ? objectMapper.createObjectNode()
                        : objectMapper.createArrayNode());
    }

    @Transactional
    public void putContent(UUID companyId, String kind, JsonNode payload, String updatedBy) {
        requireKind(kind);
        boolean object = OBJECT_KINDS.contains(kind);
        if (payload == null || (object ? !payload.isObject() : !payload.isArray())) {
            throw new ValidationException(kind + " must be a JSON " + (object ? "object" : "array"));
        }
        store.putContent(companyId, kind, write(payload), updatedBy);
    }

    private static void requireKind(String kind) {
        if (!CONTENT_KINDS.contains(kind)) {
            throw new NotFoundException("Unknown content kind: " + kind);
        }
    }

    /**
     * Development convenience, off unless {@code bootstrap-tenant-key} is set:
     * the only company on the platform answers to that key.
     */
    private Optional<TenantStore.TenantRow> bootstrap(String tenantKey) {
        String bootstrapKey = properties.bootstrapTenantKey();
        if (!StringUtils.hasText(bootstrapKey) || !bootstrapKey.equalsIgnoreCase(tenantKey)
                || store.anyTenant()) {
            return Optional.empty();
        }
        List<TenantStore.CompanyRow> companies = store.companies();
        if (companies.size() != 1) {
            return Optional.empty();
        }
        log.info("Bootstrapping storefront tenant '{}' for company {}", bootstrapKey,
                companies.get(0).name());
        store.upsert(companies.get(0).id(), bootstrapKey.toLowerCase(), "{}", true);
        return store.byKey(bootstrapKey);
    }

    private JsonNode effectiveConfig(UUID companyId, String key, String storedJson) {
        ObjectNode config = objectMapper.createObjectNode();
        // Everything the app can do is on until the shop turns it off.
        ObjectNode flags = config.putObject("featureFlags");
        for (String flag : List.of("reviews", "goldRates", "wishlist", "themePicker",
                "commission", "stories")) {
            flags.put(flag, true);
        }
        config.putArray("supportedLocales").add("en").add("lo");
        store.company(companyId).ifPresent(company -> {
            config.put("brandName", company.name());
            ObjectNode currency = config.putObject("currency");
            String code = company.currency() == null ? "LAK" : company.currency();
            currency.put("code", code);
            currency.put("symbol", "LAK".equals(code) ? "₭" : code);
            currency.put("decimals", "LAK".equals(code) ? 0 : 2);
            currency.put("symbolFirst", true);
            ObjectNode contact = config.putObject("contact");
            putIfText(contact, "phone", company.phone());
            putIfText(contact, "email", company.email());
            putIfText(contact, "storeAddress", company.address());
        });
        JsonNode stored = parse(storedJson);
        if (stored.isObject()) {
            // Top-level replace, except the blocks the defaults also fill.
            stored.fields().forEachRemaining(field -> {
                JsonNode existing = config.get(field.getKey());
                if (existing instanceof ObjectNode target && field.getValue().isObject()
                        && Set.of("currency", "contact", "featureFlags").contains(field.getKey())) {
                    target.setAll((ObjectNode) field.getValue());
                } else {
                    config.set(field.getKey(), field.getValue());
                }
            });
        }
        config.put("key", key);
        return config;
    }

    private static void putIfText(ObjectNode node, String field, String value) {
        if (StringUtils.hasText(value)) {
            node.put(field, value);
        }
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored storefront JSON is not valid", ex);
        }
    }

    private String write(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialise storefront JSON", ex);
        }
    }
}
