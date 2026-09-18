package com.finotech.jewellery.modules.storefront.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The public catalogue read model: a company's sellable stock with everything
 * the app shows, in a handful of flat queries. Like the staff catalogue, no
 * cost, supplier or location column is reachable from here.
 */
@Repository
@RequiredArgsConstructor
public class StorefrontCatalogueQuery {

    private final JdbcTemplate jdbc;

    public record ItemRow(UUID id, String itemCode, UUID productId, UUID designId, UUID branchId,
                          String productName, String description, String categoryCode,
                          String categoryName, String designName, String metalName,
                          String purityCode, String purityName, BigDecimal grossWeight,
                          BigDecimal netMetalWeight, int stoneCount, BigDecimal totalCarat,
                          String hallmarkNumber, String currency, Instant createdAt,
                          String retailJson) {
    }

    public record ReviewStats(double rating, int count) {
    }

    public record CategoryRow(String code, String name, String description, int productCount) {
    }

    /**
     * Sellable stock only, and not a piece already promised to an open online
     * order: the item stays AVAILABLE in inventory until staff raise the sale,
     * so the storefront has to hide it itself.
     */
    public List<ItemRow> sellable(UUID companyId) {
        return jdbc.query("""
                SELECT i.id, i.item_code, i.product_id, i.design_id, i.current_branch_id,
                       p.name AS product_name, p.description,
                       lower(c.code) AS category_code, c.name AS category_name,
                       d.name AS design_name, m.name AS metal_name,
                       pu.code AS purity_code, pu.name AS purity_name,
                       i.gross_weight, i.net_metal_weight, i.stone_count, i.total_carat,
                       i.hallmark_number, i.currency, i.created_at,
                       r.attributes::text AS retail
                FROM inventory.jewellery_item i
                JOIN organization.branch b    ON b.id = i.current_branch_id
                JOIN product.product p        ON p.id = i.product_id
                LEFT JOIN product.product_category c ON c.id = p.category_id
                LEFT JOIN product.jewellery_design d ON d.id = i.design_id
                LEFT JOIN product.metal m            ON m.id = i.metal_id
                LEFT JOIN product.purity pu          ON pu.id = i.purity_id
                LEFT JOIN storefront.product_retail r ON r.product_id = p.id
                WHERE i.status = 'AVAILABLE'
                  AND b.company_id = ?
                  AND NOT EXISTS (SELECT 1 FROM storefront.online_order_item oi
                                   WHERE oi.item_id = i.id AND NOT oi.released)
                ORDER BY p.name, i.item_code
                """, (rs, n) -> new ItemRow(
                rs.getObject("id", UUID.class),
                rs.getString("item_code"),
                rs.getObject("product_id", UUID.class),
                rs.getObject("design_id", UUID.class),
                rs.getObject("current_branch_id", UUID.class),
                rs.getString("product_name"),
                rs.getString("description"),
                rs.getString("category_code"),
                rs.getString("category_name"),
                rs.getString("design_name"),
                rs.getString("metal_name"),
                rs.getString("purity_code"),
                rs.getString("purity_name"),
                rs.getBigDecimal("gross_weight"),
                rs.getBigDecimal("net_metal_weight"),
                rs.getInt("stone_count"),
                rs.getBigDecimal("total_carat"),
                rs.getString("hallmark_number"),
                rs.getString("currency"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("retail")), companyId);
    }

    /** Item photographs, primary first, for every sellable item of the company. */
    public Map<UUID, List<String>> itemImages(UUID companyId) {
        return images("""
                SELECT im.jewellery_item_id, im.storage_key
                FROM inventory.item_image im
                JOIN inventory.jewellery_item i ON i.id = im.jewellery_item_id
                JOIN organization.branch b ON b.id = i.current_branch_id
                WHERE i.status = 'AVAILABLE' AND b.company_id = ?
                ORDER BY im.primary_image DESC, im.display_order NULLS LAST, im.created_at
                """, companyId);
    }

    public Map<UUID, List<String>> productImages(UUID companyId) {
        return images("""
                SELECT im.product_id, im.storage_key
                FROM product.product_image im
                JOIN product.product p ON p.id = im.product_id
                WHERE p.company_id = ?
                ORDER BY im.primary_image DESC, im.display_order NULLS LAST, im.created_at
                """, companyId);
    }

    public Map<UUID, List<String>> designImages(UUID companyId) {
        return images("""
                SELECT im.design_id, im.storage_key
                FROM product.design_image im
                JOIN product.jewellery_design d ON d.id = im.design_id
                WHERE d.company_id = ?
                ORDER BY im.primary_image DESC, im.display_order NULLS LAST, im.created_at
                """, companyId);
    }

    private Map<UUID, List<String>> images(String sql, UUID companyId) {
        Map<UUID, List<String>> result = new HashMap<>();
        jdbc.query(sql, rs -> {
            result.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                    .add(rs.getString(2));
        }, companyId);
        return result;
    }

    public Map<UUID, ReviewStats> reviewStats(UUID companyId) {
        Map<UUID, ReviewStats> result = new HashMap<>();
        jdbc.query("""
                SELECT product_id, avg(rating) AS rating, count(*) AS reviews
                FROM storefront.review WHERE company_id = ? AND published
                GROUP BY product_id
                """, rs -> {
            result.put(rs.getObject("product_id", UUID.class),
                    new ReviewStats(rs.getDouble("rating"), rs.getInt("reviews")));
        }, companyId);
        return result;
    }

    /** Active categories of the company; counts are filled in from the priced catalogue. */
    public List<CategoryRow> categories(UUID companyId) {
        return jdbc.query("""
                SELECT lower(code) AS code, name, description
                FROM product.product_category
                WHERE company_id = ? AND status = 'ACTIVE'
                ORDER BY display_order NULLS LAST, name
                """, (rs, n) -> new CategoryRow(rs.getString("code"), rs.getString("name"),
                rs.getString("description"), 0), companyId);
    }

    /**
     * Whether a storage key is artwork the public may fetch: a photograph of a
     * product, design or item of this platform, or a file uploaded for the
     * storefront itself. Customer documents and certificates share the file
     * store and must never be reachable by guessing a key.
     */
    public boolean isPublicImage(String storageKey) {
        if (storageKey.startsWith("storefront/")) {
            return true;
        }
        Boolean found = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM inventory.item_image WHERE storage_key = ?)
                    OR EXISTS (SELECT 1 FROM product.product_image WHERE storage_key = ?)
                    OR EXISTS (SELECT 1 FROM product.design_image WHERE storage_key = ?)
                """, Boolean.class, storageKey, storageKey, storageKey);
        return Boolean.TRUE.equals(found);
    }
}
