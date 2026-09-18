package com.finotech.jewellery.modules.storefront.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finotech.jewellery.modules.organization.application.CompanyScope;
import com.finotech.jewellery.modules.storefront.api.StorefrontAdminController;
import com.finotech.jewellery.modules.storefront.infrastructure.CustomerAppStore;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Staff maintenance of what the customer app shows. */
@Service
@RequiredArgsConstructor
public class StorefrontAdminService {

    private final JdbcTemplate jdbc;
    private final CompanyScope companyScope;
    private final CustomerAppStore customers;
    private final StorefrontCatalogueService catalogue;
    private final ObjectMapper objectMapper;

    @Transactional
    public JsonNode putRetail(UUID productId, JsonNode attributes) {
        if (attributes == null || !attributes.isObject()) {
            throw new ValidationException("Retail attributes must be a JSON object");
        }
        UUID companyId = requireProductCompany(productId);
        try {
            jdbc.update("""
                    INSERT INTO storefront.product_retail (product_id, attributes)
                    VALUES (?, ?::jsonb)
                    ON CONFLICT (product_id) DO UPDATE
                       SET attributes = EXCLUDED.attributes, updated_at = now()
                    """, productId, objectMapper.writeValueAsString(attributes));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
        catalogue.evict(companyId);
        return attributes;
    }

    @Transactional
    public UUID addReview(StorefrontAdminController.ReviewRequest request) {
        if (request.productId() == null || !StringUtils.hasText(request.author())
                || !StringUtils.hasText(request.body())
                || request.rating() < 1 || request.rating() > 5) {
            throw new ValidationException("A review needs a product, an author, a body and a rating of 1-5");
        }
        UUID companyId = requireProductCompany(request.productId());
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO storefront.review (id, company_id, product_id, author, avatar, rating,
                    body, verified, featured)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, companyId, request.productId(), request.author().trim(), request.avatar(),
                request.rating(), request.body().trim(), request.verified(), request.featured());
        catalogue.evict(companyId);
        return id;
    }

    @Transactional
    public void broadcast(UUID companyId, StorefrontAdminController.BroadcastRequest request) {
        if (!StringUtils.hasText(request.title()) || !StringUtils.hasText(request.body())) {
            throw new ValidationException("A notification needs a title and a body");
        }
        customers.notify(companyId, null,
                StringUtils.hasText(request.kind()) ? request.kind().trim().toLowerCase() : "info",
                request.title().trim(), request.body().trim(), request.link());
    }

    /** The product's company, refused when it is not the caller's. */
    private UUID requireProductCompany(UUID productId) {
        UUID scope = companyScope.currentOrNull();
        return jdbc.query("SELECT company_id FROM product.product WHERE id = ?",
                        (rs, n) -> rs.getObject(1, UUID.class), productId).stream()
                .filter(owner -> scope == null || scope.equals(owner))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }
}
