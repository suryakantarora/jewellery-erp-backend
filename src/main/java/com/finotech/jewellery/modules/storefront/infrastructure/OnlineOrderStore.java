package com.finotech.jewellery.modules.storefront.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Online orders placed from the customer app. */
@Repository
@RequiredArgsConstructor
public class OnlineOrderStore {

    private final JdbcTemplate jdbc;

    public record OrderRow(UUID id, UUID customerId, String orderNumber, String status,
                           BigDecimal subtotal, BigDecimal shipping, BigDecimal tax,
                           BigDecimal discount, BigDecimal total, String currency,
                           String offerCode, String addressText, String contactName,
                           String contactPhone, String paymentKind, String paymentLabel,
                           String courier, String trackingNumber, String etaJson,
                           Instant placedAt, String customerName, List<ItemRow> items) {

        OrderRow withItems(List<ItemRow> items) {
            return new OrderRow(id, customerId, orderNumber, status, subtotal, shipping, tax,
                    discount, total, currency, offerCode, addressText, contactName, contactPhone,
                    paymentKind, paymentLabel, courier, trackingNumber, etaJson, placedAt,
                    customerName, items);
        }
    }

    public record ItemRow(UUID itemId, String itemCode, String name, String image,
                          BigDecimal price, int quantity, String size) {
    }

    private static final String SELECT = """
            SELECT o.id, o.customer_id, o.order_number, o.status, o.subtotal, o.shipping, o.tax,
                   o.discount, o.total, o.currency, o.offer_code, o.address_text, o.contact_name,
                   o.contact_phone, o.payment_kind, o.payment_label, o.courier, o.tracking_number,
                   o.eta::text AS eta, o.placed_at, c.full_name AS customer_name
            FROM storefront.online_order o
            JOIN customer.customer c ON c.id = o.customer_id
            """;

    private static final RowMapper<OrderRow> MAPPER = (rs, n) -> new OrderRow(
            rs.getObject("id", UUID.class), rs.getObject("customer_id", UUID.class),
            rs.getString("order_number"), rs.getString("status"), rs.getBigDecimal("subtotal"),
            rs.getBigDecimal("shipping"), rs.getBigDecimal("tax"), rs.getBigDecimal("discount"),
            rs.getBigDecimal("total"), rs.getString("currency"), rs.getString("offer_code"),
            rs.getString("address_text"), rs.getString("contact_name"),
            rs.getString("contact_phone"), rs.getString("payment_kind"),
            rs.getString("payment_label"), rs.getString("courier"),
            rs.getString("tracking_number"), rs.getString("eta"),
            rs.getTimestamp("placed_at").toInstant(), rs.getString("customer_name"), List.of());

    public List<OrderRow> ofCustomer(UUID companyId, UUID customerId) {
        return withItems(jdbc.query(SELECT + """
                 WHERE o.company_id = ? AND o.customer_id = ? ORDER BY o.placed_at DESC LIMIT 200
                """, MAPPER, companyId, customerId));
    }

    public Optional<OrderRow> ofCustomerByNumber(UUID companyId, UUID customerId, String number) {
        return withItems(jdbc.query(SELECT + """
                 WHERE o.company_id = ? AND o.customer_id = ? AND o.order_number = ?
                """, MAPPER, companyId, customerId, number)).stream().findFirst();
    }

    public Optional<OrderRow> byNumber(UUID companyId, String number) {
        return withItems(jdbc.query(SELECT + " WHERE o.company_id = ? AND o.order_number = ?",
                MAPPER, companyId, number)).stream().findFirst();
    }

    /** Staff view. {@code companyId} null is the platform administrator's view of everything. */
    public List<OrderRow> forStaff(UUID companyId, String status, int limit, int offset) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (companyId != null) {
            sql.append(" AND o.company_id = ?");
            args.add(companyId);
        }
        if (status != null) {
            sql.append(" AND o.status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY o.placed_at DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return withItems(jdbc.query(sql.toString(), MAPPER, args.toArray()));
    }

    public Optional<UUID> companyOf(String orderNumber, UUID companyIdOrNull) {
        return jdbc.query("""
                SELECT company_id FROM storefront.online_order
                WHERE order_number = ? AND (?::uuid IS NULL OR company_id = ?::uuid)
                """, (rs, n) -> rs.getObject(1, UUID.class), orderNumber,
                companyIdOrNull == null ? null : companyIdOrNull.toString(),
                companyIdOrNull == null ? null : companyIdOrNull.toString()).stream().findFirst();
    }

    public boolean numberExists(UUID companyId, String number) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM storefront.online_order WHERE company_id = ? AND order_number = ?
                """, Integer.class, companyId, number);
        return count != null && count > 0;
    }

    public UUID insert(UUID companyId, OrderRow order) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO storefront.online_order (id, company_id, customer_id, order_number, status,
                    subtotal, shipping, tax, discount, total, currency, offer_code, address_text,
                    contact_name, contact_phone, payment_kind, payment_label, eta)
                VALUES (?, ?, ?, ?, 'placed', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """, id, companyId, order.customerId(), order.orderNumber(), order.subtotal(),
                order.shipping(), order.tax(), order.discount(), order.total(), order.currency(),
                order.offerCode(), order.addressText(), order.contactName(), order.contactPhone(),
                order.paymentKind(), order.paymentLabel(), order.etaJson());
        for (ItemRow item : order.items()) {
            // uq_online_order_item_open refuses a piece that is already in an
            // open order; the caller turns that into a conflict.
            jdbc.update("""
                    INSERT INTO storefront.online_order_item (order_id, item_id, item_code, name,
                        image, price, quantity, size)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, item.itemId(), item.itemCode(), item.name(), item.image(),
                    item.price(), item.quantity(), item.size());
        }
        return id;
    }

    public void updateStatus(UUID orderId, String status, String courier, String trackingNumber,
                             String etaJson, String updatedBy) {
        jdbc.update("""
                UPDATE storefront.online_order
                   SET status = ?, courier = coalesce(?, courier),
                       tracking_number = coalesce(?, tracking_number),
                       eta = coalesce(?::jsonb, eta), updated_at = now(), updated_by = ?
                 WHERE id = ?
                """, status, courier, trackingNumber, etaJson, updatedBy, orderId);
        if ("cancelled".equals(status)) {
            jdbc.update("UPDATE storefront.online_order_item SET released = true WHERE order_id = ?",
                    orderId);
        }
    }

    private List<OrderRow> withItems(List<OrderRow> orders) {
        if (orders.isEmpty()) {
            return orders;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(orders.size(), "?"));
        Map<UUID, List<ItemRow>> byOrder = new HashMap<>();
        jdbc.query("""
                SELECT order_id, item_id, item_code, name, image, price, quantity, size
                FROM storefront.online_order_item WHERE order_id IN (%s) ORDER BY id
                """.formatted(placeholders), rs -> {
            byOrder.computeIfAbsent(rs.getObject("order_id", UUID.class), k -> new ArrayList<>())
                    .add(new ItemRow(rs.getObject("item_id", UUID.class), rs.getString("item_code"),
                            rs.getString("name"), rs.getString("image"), rs.getBigDecimal("price"),
                            rs.getInt("quantity"), rs.getString("size")));
        }, orders.stream().map(OrderRow::id).toArray());
        return orders.stream().map(o -> o.withItems(byOrder.getOrDefault(o.id(), List.of())))
                .toList();
    }
}
