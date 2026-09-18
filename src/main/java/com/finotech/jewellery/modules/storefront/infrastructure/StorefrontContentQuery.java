package com.finotech.jewellery.modules.storefront.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Published metal rates and customer reviews, as the app shows them. */
@Repository
@RequiredArgsConstructor
public class StorefrontContentQuery {

    private final JdbcTemplate jdbc;

    public record RateRow(String metalName, String purityCode, String purityName,
                          BigDecimal ratePerUnit, String currency, Instant publishedAt) {
    }

    public record ReviewRow(UUID id, UUID productId, String author, String avatar,
                            BigDecimal rating, String body, boolean verified, Instant createdAt) {
    }

    /**
     * The selling rate in force today for every purity of the company: the
     * company-wide rate (no branch) where one exists, else the most recent
     * branch rate — the customer app quotes one price per purity (plan Q7).
     */
    public List<RateRow> sellingRates(UUID companyId) {
        return jdbc.query("""
                SELECT DISTINCT ON (pu.id) m.name AS metal_name, pu.code AS purity_code,
                       pu.name AS purity_name, r.rate_per_unit, r.currency, r.published_at
                FROM product.metal_rate r
                JOIN product.metal m   ON m.id = r.metal_id
                JOIN product.purity pu ON pu.id = r.purity_id
                WHERE m.company_id = ? AND r.rate_type = 'SELLING'
                  AND r.effective_date <= current_date
                ORDER BY pu.id, (r.branch_id IS NOT NULL), r.effective_date DESC, r.published_at DESC
                """, (rs, n) -> new RateRow(rs.getString("metal_name"), rs.getString("purity_code"),
                rs.getString("purity_name"), rs.getBigDecimal("rate_per_unit"),
                rs.getString("currency"), rs.getTimestamp("published_at").toInstant()), companyId);
    }

    public List<ReviewRow> reviewsOfProduct(UUID companyId, UUID productId) {
        return jdbc.query(REVIEW_SELECT + """
                 WHERE company_id = ? AND product_id = ? AND published
                 ORDER BY created_at DESC LIMIT 100
                """, (rs, n) -> reviewRow(rs), companyId, productId);
    }

    public List<ReviewRow> featuredReviews(UUID companyId) {
        return jdbc.query(REVIEW_SELECT + """
                 WHERE company_id = ? AND featured AND published
                 ORDER BY created_at DESC LIMIT 20
                """, (rs, n) -> reviewRow(rs), companyId);
    }

    private static final String REVIEW_SELECT = """
            SELECT id, product_id, author, avatar, rating, body, verified, created_at
            FROM storefront.review
            """;

    private static ReviewRow reviewRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ReviewRow(rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getString("author"), rs.getString("avatar"), rs.getBigDecimal("rating"),
                rs.getString("body"), rs.getBoolean("verified"),
                rs.getTimestamp("created_at").toInstant());
    }
}
