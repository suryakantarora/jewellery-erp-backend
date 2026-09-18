package com.finotech.jewellery.modules.storefront.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finotech.jewellery.modules.storefront.infrastructure.CustomerAppStore;
import com.finotech.jewellery.modules.storefront.infrastructure.CustomerAppStore.AddressRow;
import com.finotech.jewellery.modules.storefront.infrastructure.CustomerAppStore.PaymentRow;
import com.finotech.jewellery.modules.storefront.infrastructure.OnlineOrderStore;
import com.finotech.jewellery.modules.storefront.infrastructure.OnlineOrderStore.ItemRow;
import com.finotech.jewellery.modules.storefront.infrastructure.OnlineOrderStore.OrderRow;
import com.finotech.jewellery.shared.exception.BusinessException;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Online orders.
 *
 * <p>The app sends what the customer chose — pieces, address, payment method,
 * coupon — and never an amount. Every figure on the order is worked out here
 * from the live catalogue price and the tenant's delivery and tax settings, by
 * the same rules the app uses to show the bag, so what the customer saw is what
 * is recorded unless a rate moved in between; then the server's figure stands.
 *
 * <p>An order does not touch inventory: the piece stays AVAILABLE until staff
 * raise the sale, and is hidden from the storefront (and from a second order)
 * while the online order is open.
 */
@Service
@RequiredArgsConstructor
public class OnlineOrderService {

    public static final List<String> STAGES =
            List.of("placed", "confirmed", "packed", "shipped", "delivered");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OnlineOrderStore orders;
    private final CustomerAppStore customers;
    private final CustomerAuthService auth;
    private final StorefrontCatalogueService catalogue;
    private final TenantService tenants;
    private final CustomerPushSender push;
    private final ObjectMapper objectMapper;

    public record Line(UUID itemId, int quantity, String size) {
    }

    public record PlaceOrder(List<Line> lines, UUID addressId, UUID paymentMethodId,
                             String offerCode) {
    }

    public List<ObjectNode> list() {
        return orders.ofCustomer(auth.principal().companyId(), auth.requireCustomer().id())
                .stream().map(this::toJson).toList();
    }

    public ObjectNode get(String orderNumber) {
        return orders.ofCustomerByNumber(auth.principal().companyId(),
                        auth.requireCustomer().id(), orderNumber)
                .map(this::toJson)
                .orElseThrow(() -> new NotFoundException("Order not found"));
    }

    @Transactional
    public ObjectNode place(PlaceOrder request) {
        CustomerAppStore.AccountRow customer = auth.requireCustomer();
        UUID companyId = auth.principal().companyId();
        Tenant tenant = tenants.ofCompany(companyId)
                .orElseThrow(() -> new NotFoundException("This shop is not open online"));

        if (request.lines() == null || request.lines().isEmpty()) {
            throw new ValidationException("An order needs at least one piece");
        }
        AddressRow address = customers.addresses(customer.id()).stream()
                .filter(a -> a.id().equals(request.addressId())).findFirst()
                .orElseThrow(() -> new ValidationException("Choose a delivery address"));
        PaymentRow payment = customers.paymentMethods(customer.id()).stream()
                .filter(p -> p.id().equals(request.paymentMethodId())).findFirst()
                .orElseThrow(() -> new ValidationException("Choose a payment method"));

        int decimals = tenant.config().path("currency").path("decimals").asInt(0);
        List<ItemRow> items = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        BigDecimal running = BigDecimal.ZERO;
        String currency = null;
        for (Line line : request.lines()) {
            if (line.itemId() == null || !seen.add(line.itemId())) {
                throw new ValidationException("Each piece may appear once in an order");
            }
            // Every piece is one of a kind: there is no second one to send.
            if (line.quantity() != 1) {
                throw new BusinessException("Only one of each piece is available");
            }
            StorefrontCatalogueService.Entry entry = catalogue.item(companyId, line.itemId())
                    .orElseThrow(() -> new ConflictException(
                            "A piece in your bag is no longer available"));
            currency = entry.currency();
            running = running.add(entry.price());
            items.add(new ItemRow(entry.id(), entry.itemCode(), entry.name(), entry.image(),
                    entry.price(), 1, StringUtils.hasText(line.size()) ? line.size() : null));
        }

        final BigDecimal subtotal = running;
        Optional<JsonNode> offer = offer(companyId, request.offerCode(), subtotal);
        BigDecimal discount = offer.map(o -> discountOf(o, subtotal, decimals)).orElse(BigDecimal.ZERO);
        boolean freeByOffer = offer.map(o -> isFreeDelivery(o.path("kind").asText())).orElse(false);
        BigDecimal shipping = freeByOffer
                || subtotal.compareTo(tenant.decimal("freeDeliveryThreshold", "0")) >= 0
                ? BigDecimal.ZERO : tenant.decimal("deliveryFee", "0");
        BigDecimal tax = subtotal.subtract(discount).multiply(tenant.decimal("taxRate", "0"))
                .setScale(decimals, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.subtract(discount).add(shipping).add(tax);

        String number = orderNumber(companyId, tenant.text("brandMark", "ORD"));
        OrderRow order = new OrderRow(null, customer.id(), number, "placed", subtotal, shipping, tax,
                discount, total, currency, offer.map(o -> o.path("code").asText()).orElse(null),
                oneLine(address), address.name(), address.phone(), payment.kind(), payment.label(),
                null, null, null, Instant.now(), null, items);
        try {
            orders.insert(companyId, order);
        } catch (DuplicateKeyException ex) {
            throw new ConflictException("A piece in your bag has just been ordered by someone else");
        }

        customers.replaceCart(customer.id(), List.of());
        catalogue.evict(companyId);
        announce(companyId, customer.id(), "Order placed",
                "We have received your order " + number + " and will confirm it shortly.", number);
        return get(number);
    }

    // ---------- staff ----------

    public List<ObjectNode> forStaff(UUID companyIdOrNull, String status, int limit, int offset) {
        return orders.forStaff(companyIdOrNull, status, Math.min(Math.max(limit, 1), 200),
                        Math.max(offset, 0))
                .stream().map(o -> {
                    ObjectNode node = toJson(o);
                    node.put("customerId", o.customerId().toString());
                    node.put("customerName", o.customerName());
                    node.put("contactName", o.contactName());
                    node.put("contactPhone", o.contactPhone());
                    node.put("discount", o.discount());
                    node.put("currency", o.currency());
                    return node;
                }).toList();
    }

    @Transactional
    public ObjectNode updateStatus(UUID companyIdOrNull, String orderNumber, String status,
                                   String courier, String trackingNumber, JsonNode eta,
                                   String updatedBy) {
        UUID companyId = orders.companyOf(orderNumber, companyIdOrNull)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        OrderRow order = orders.byNumber(companyId, orderNumber).orElseThrow();
        String next = status == null ? "" : status.trim().toLowerCase();
        if (!STAGES.contains(next) && !"cancelled".equals(next)) {
            throw new ValidationException("status must be one of " + STAGES + " or cancelled");
        }
        if ("cancelled".equals(order.status()) || "delivered".equals(order.status())) {
            throw new BusinessException("A " + order.status() + " order cannot change");
        }
        if (STAGES.contains(next) && STAGES.indexOf(next) < STAGES.indexOf(order.status())) {
            throw new BusinessException("An order cannot move back from " + order.status());
        }
        orders.updateStatus(order.id(), next, blankToNull(courier), blankToNull(trackingNumber),
                eta == null || eta.isNull() ? null : write(eta), updatedBy);
        catalogue.evict(companyId);

        if (!next.equals(order.status())) {
            announce(companyId, order.customerId(), headline(next),
                    "Your order " + orderNumber + " is " + describe(next) + ".", orderNumber);
        }
        return toJson(orders.byNumber(companyId, orderNumber).orElseThrow());
    }

    // ---------- helpers ----------

    private void announce(UUID companyId, UUID customerId, String title, String body,
                          String orderNumber) {
        String link = "/orders/" + orderNumber;
        customers.notify(companyId, customerId, "order", title, body, link);
        push.send(customerId, title, body, link);
    }

    private Optional<JsonNode> offer(UUID companyId, String code, BigDecimal subtotal) {
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }
        for (JsonNode offer : tenants.content(companyId, "offers")) {
            if (!code.trim().equalsIgnoreCase(offer.path("code").asText())) {
                continue;
            }
            String expires = offer.path("expires").asText("");
            boolean expired = false;
            try {
                expired = !expires.isEmpty() && java.time.LocalDateTime.parse(
                        expires.length() > 19 ? expires.substring(0, 19) : expires)
                        .isBefore(java.time.LocalDateTime.now());
            } catch (java.time.format.DateTimeParseException ignored) {
                // An unreadable date is treated as open-ended, as the app does.
            }
            if (expired) {
                throw new BusinessException("This offer has expired");
            }
            // Below the minimum the app shows the offer as not yet earned and
            // charges full price; the order does the same rather than failing.
            return subtotal.compareTo(offer.path("minSubtotal").decimalValue()) >= 0
                    ? Optional.of(offer) : Optional.empty();
        }
        throw new BusinessException("This offer code is not valid");
    }

    private static BigDecimal discountOf(JsonNode offer, BigDecimal subtotal, int decimals) {
        BigDecimal value = offer.path("value").decimalValue();
        return switch (offer.path("kind").asText()) {
            case "percent" -> subtotal.multiply(value).divide(BigDecimal.valueOf(100), decimals,
                    RoundingMode.HALF_UP);
            case "amount" -> value.max(BigDecimal.ZERO).min(subtotal);
            default -> BigDecimal.ZERO;
        };
    }

    private static boolean isFreeDelivery(String kind) {
        return "freeDelivery".equalsIgnoreCase(kind) || "free_delivery".equalsIgnoreCase(kind);
    }

    private String orderNumber(UUID companyId, String brandMark) {
        String prefix = brandMark.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        prefix = prefix.isEmpty() ? "ORD" : prefix.substring(0, Math.min(prefix.length(), 8));
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = prefix + "-" + (100000 + RANDOM.nextInt(900000));
            if (!orders.numberExists(companyId, candidate)) {
                return candidate;
            }
        }
        return prefix + "-" + System.currentTimeMillis();
    }

    private static String oneLine(AddressRow a) {
        StringBuilder sb = new StringBuilder(a.line1());
        if (StringUtils.hasText(a.city())) {
            sb.append(", ").append(a.city());
        }
        if (StringUtils.hasText(a.postcode())) {
            sb.append(' ').append(a.postcode());
        }
        return sb.toString();
    }

    private static String headline(String status) {
        return switch (status) {
            case "confirmed" -> "Order confirmed";
            case "packed" -> "Order packed";
            case "shipped" -> "Out for delivery";
            case "delivered" -> "Delivered";
            case "cancelled" -> "Order cancelled";
            default -> "Order update";
        };
    }

    private static String describe(String status) {
        return switch (status) {
            case "shipped" -> "on its way";
            case "cancelled" -> "cancelled";
            default -> status;
        };
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ObjectNode toJson(OrderRow order) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", order.orderNumber());
        node.put("date", order.placedAt().toString());
        node.put("status", order.status());
        ArrayNode items = node.putArray("items");
        for (ItemRow item : order.items()) {
            ObjectNode line = items.addObject();
            line.put("productId", item.itemId() == null ? "" : item.itemId().toString());
            line.put("name", item.name());
            line.put("image", item.image() == null ? "" : item.image());
            line.put("price", item.price());
            line.put("quantity", item.quantity());
            if (item.size() != null) {
                line.put("size", item.size());
            }
        }
        // The app's total is subtotal + shipping + tax; a discount is folded
        // into the subtotal it shows so the three still add up.
        node.put("subtotal", order.subtotal().subtract(order.discount()));
        node.put("shipping", order.shipping());
        node.put("tax", order.tax());
        node.put("total", order.total());
        node.put("address", order.addressText());
        node.put("paymentKind", order.paymentKind());
        if (order.courier() != null) {
            node.put("courier", order.courier());
        }
        if (order.trackingNumber() != null) {
            node.put("trackingNumber", order.trackingNumber());
        }
        if (order.etaJson() != null) {
            try {
                node.set("eta", objectMapper.readTree(order.etaJson()));
            } catch (JsonProcessingException ignored) {
                // A malformed ETA is not worth failing the order list over.
            }
        }
        return node;
    }

    private String write(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
