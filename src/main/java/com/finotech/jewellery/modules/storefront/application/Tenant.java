package com.finotech.jewellery.modules.storefront.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.UUID;

/** A company as the customer app knows it: a key and a branding document. */
public record Tenant(UUID companyId, String key, JsonNode config) {

    public BigDecimal decimal(String field, String fallback) {
        JsonNode node = config.get(field);
        return node != null && node.isNumber() ? node.decimalValue() : new BigDecimal(fallback);
    }

    public String text(String field, String fallback) {
        JsonNode node = config.get(field);
        return node != null && node.isTextual() ? node.asText() : fallback;
    }
}
