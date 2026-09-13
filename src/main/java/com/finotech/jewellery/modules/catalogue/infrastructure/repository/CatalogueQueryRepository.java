package com.finotech.jewellery.modules.catalogue.infrastructure.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The catalogue read model.
 *
 * <p>A flat query rather than entity graphs, following
 * {@code ReportingQueryRepository}: this crosses six tables in three schemas to
 * produce one display row, and loading the object graph to throw most of it
 * away would cost far more than it returns.
 */
@Repository
@RequiredArgsConstructor
public class CatalogueQueryRepository {

    private final JdbcTemplate jdbc;

    /** Columns are listed explicitly; no cost column is reachable from here. */
    private static final String SELECT = """
            SELECT i.id, i.item_code, p.name AS product_name,
                   c.name AS category_name, t.name AS type_name,
                   m.name AS metal_name, pu.code AS purity_code, pu.name AS purity_name,
                   i.gross_weight, i.net_metal_weight, i.stone_count, i.total_carat,
                   i.hallmark_number, i.currency,
                   (SELECT im.storage_key FROM inventory.item_image im
                     WHERE im.jewellery_item_id = i.id AND im.primary_image
                     LIMIT 1) AS primary_image_key
            FROM inventory.jewellery_item i
            JOIN product.product p        ON p.id = i.product_id
            LEFT JOIN product.product_category c ON c.id = p.category_id
            LEFT JOIN product.product_type t     ON t.id = p.product_type_id
            LEFT JOIN product.metal m            ON m.id = i.metal_id
            LEFT JOIN product.purity pu          ON pu.id = i.purity_id
            """;

    /**
     * Sellable stock only.
     *
     * <p>{@code AVAILABLE} is the filter, not a status parameter: showing a
     * customer a piece that is reserved, sold or away for repair is worse than
     * showing them nothing, and no caller should be able to ask for that.
     */
    public List<Row> search(UUID branchId, UUID categoryId, UUID metalId, UUID purityId,
                            String search, int limit, int offset) {
        StringBuilder sql = new StringBuilder(SELECT)
                .append(" WHERE i.status = 'AVAILABLE'");
        List<Object> args = new ArrayList<>();

        if (branchId != null) {
            sql.append(" AND i.current_branch_id = ?");
            args.add(branchId);
        }
        if (categoryId != null) {
            sql.append(" AND p.category_id = ?");
            args.add(categoryId);
        }
        if (metalId != null) {
            sql.append(" AND i.metal_id = ?");
            args.add(metalId);
        }
        if (purityId != null) {
            sql.append(" AND i.purity_id = ?");
            args.add(purityId);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (lower(p.name) LIKE ? OR lower(i.item_code) LIKE ?)");
            String like = "%" + search.trim().toLowerCase() + "%";
            args.add(like);
            args.add(like);
        }

        // Photographed pieces first: a catalogue leads with what can be seen.
        //
        // Repeats the condition rather than ordering by the select alias:
        // Postgres accepts a bare output alias in ORDER BY, but not one wrapped
        // in an expression, where it resolves as a real column and fails.
        sql.append(" ORDER BY (NOT EXISTS (SELECT 1 FROM inventory.item_image im2")
           .append(" WHERE im2.jewellery_item_id = i.id AND im2.primary_image)),")
           .append(" p.name, i.item_code")
           .append(" LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);

        return jdbc.query(sql.toString(), (rs, n) -> new Row(
                rs.getObject("id", UUID.class),
                rs.getString("item_code"),
                rs.getString("product_name"),
                rs.getString("category_name"),
                rs.getString("type_name"),
                rs.getString("metal_name"),
                rs.getString("purity_code"),
                rs.getString("purity_name"),
                rs.getBigDecimal("gross_weight"),
                rs.getBigDecimal("net_metal_weight"),
                rs.getInt("stone_count"),
                rs.getBigDecimal("total_carat"),
                rs.getString("hallmark_number"),
                rs.getString("primary_image_key"),
                rs.getString("currency")), args.toArray());
    }

    public long count(UUID branchId, UUID categoryId, UUID metalId, UUID purityId, String search) {
        StringBuilder sql = new StringBuilder("""
                SELECT count(*) FROM inventory.jewellery_item i
                JOIN product.product p ON p.id = i.product_id
                WHERE i.status = 'AVAILABLE'
                """);
        List<Object> args = new ArrayList<>();
        if (branchId != null) {
            sql.append(" AND i.current_branch_id = ?");
            args.add(branchId);
        }
        if (categoryId != null) {
            sql.append(" AND p.category_id = ?");
            args.add(categoryId);
        }
        if (metalId != null) {
            sql.append(" AND i.metal_id = ?");
            args.add(metalId);
        }
        if (purityId != null) {
            sql.append(" AND i.purity_id = ?");
            args.add(purityId);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (lower(p.name) LIKE ? OR lower(i.item_code) LIKE ?)");
            String like = "%" + search.trim().toLowerCase() + "%";
            args.add(like);
            args.add(like);
        }
        Long total = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    public record Row(UUID id, String itemCode, String productName, String categoryName,
                      String typeName, String metalName, String purityCode, String purityName,
                      BigDecimal grossWeight, BigDecimal netMetalWeight, int stoneCount,
                      BigDecimal totalCarat, String hallmarkNumber, String primaryImageKey,
                      String currency) {
    }
}
