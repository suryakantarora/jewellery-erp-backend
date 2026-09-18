package com.finotech.jewellery.modules.storefront.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Everything the app keeps per customer: identity, OTP challenges, bag,
 * wishlist, addresses, payment methods, inbox and devices.
 *
 * <p>Every statement names the customer (or company) it is for; there is no
 * lookup by bare row id, so one customer cannot reach another's rows by
 * guessing an identifier.
 */
@Repository
@RequiredArgsConstructor
public class CustomerAppStore {

    private static final String ACTOR = "customer-app";

    private final JdbcTemplate jdbc;

    // ---------- identity ----------

    public record AccountRow(UUID id, String fullName, String phone, String email, String status,
                             Instant memberSince, boolean profileComplete, String avatar,
                             int tokenVersion, String tier) {
    }

    private static final String ACCOUNT_SELECT = """
            SELECT c.id, c.full_name, c.phone, c.email, c.status, c.created_at,
                   coalesce(p.profile_complete, true) AS profile_complete, p.avatar,
                   coalesce(p.token_version, 0) AS token_version,
                   (SELECT t.name FROM crm.loyalty_account a
                      JOIN crm.loyalty_tier t ON t.id = a.tier_id
                     WHERE a.customer_id = c.id LIMIT 1) AS tier
            FROM customer.customer c
            LEFT JOIN storefront.customer_profile p ON p.customer_id = c.id
            """;

    public Optional<AccountRow> account(UUID companyId, UUID customerId) {
        return jdbc.query(ACCOUNT_SELECT + " WHERE c.company_id = ? AND c.id = ?",
                (rs, n) -> accountRow(rs), companyId, customerId).stream().findFirst();
    }

    /** Matches on digits, so "+856 20 5555 0142" typed at the counter is the same person. */
    public Optional<AccountRow> accountByPhone(UUID companyId, String normalizedPhone) {
        return jdbc.query(ACCOUNT_SELECT + """
                 WHERE c.company_id = ?
                   AND regexp_replace(c.phone, '[^0-9]', '', 'g') = regexp_replace(?, '[^0-9]', '', 'g')
                 ORDER BY c.created_at LIMIT 1
                """, (rs, n) -> accountRow(rs), companyId, normalizedPhone).stream().findFirst();
    }

    private static AccountRow accountRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AccountRow(rs.getObject("id", UUID.class), rs.getString("full_name"),
                rs.getString("phone"), rs.getString("email"), rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(), rs.getBoolean("profile_complete"),
                rs.getString("avatar"), rs.getInt("token_version"), rs.getString("tier"));
    }

    public UUID createCustomer(UUID companyId, String customerCode, String phone) {
        UUID id = UUID.randomUUID();
        // full_name is mandatory on the master; the phone stands in until the
        // customer types their name (customer_profile.profile_complete).
        jdbc.update("""
                INSERT INTO customer.customer (id, version, company_id, customer_code, customer_type,
                    full_name, phone, kyc_status, status, notes,
                    created_at, created_by, updated_at, updated_by)
                VALUES (?, 0, ?, ?, 'INDIVIDUAL', ?, ?, 'NOT_REQUIRED', 'ACTIVE',
                    'Registered through the customer app', now(), ?, now(), ?)
                """, id, companyId, customerCode, phone, phone, ACTOR, ACTOR);
        jdbc.update("""
                INSERT INTO storefront.customer_profile (customer_id, profile_complete, last_login_at)
                VALUES (?, false, now())
                """, id);
        return id;
    }

    public boolean customerCodeExists(UUID companyId, String code) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM customer.customer WHERE company_id = ? AND upper(customer_code) = upper(?)
                """, Integer.class, companyId, code);
        return count != null && count > 0;
    }

    /** A customer the shop registered at the counter signs in for the first time. */
    public void touchLogin(UUID customerId) {
        jdbc.update("""
                INSERT INTO storefront.customer_profile (customer_id, profile_complete, last_login_at)
                VALUES (?, true, now())
                ON CONFLICT (customer_id) DO UPDATE SET last_login_at = now()
                """, customerId);
    }

    public void updateProfile(UUID companyId, UUID customerId, String fullName, String email,
                              String avatar) {
        jdbc.update("""
                UPDATE customer.customer
                   SET full_name = ?, email = ?, updated_at = now(), updated_by = ?,
                       version = version + 1
                 WHERE company_id = ? AND id = ?
                """, fullName, email, ACTOR, companyId, customerId);
        jdbc.update("""
                INSERT INTO storefront.customer_profile (customer_id, profile_complete, avatar)
                VALUES (?, true, ?)
                ON CONFLICT (customer_id) DO UPDATE
                   SET profile_complete = true, avatar = EXCLUDED.avatar
                """, customerId, avatar);
    }

    public void bumpTokenVersion(UUID customerId) {
        jdbc.update("""
                UPDATE storefront.customer_profile SET token_version = token_version + 1
                 WHERE customer_id = ?
                """, customerId);
    }

    // ---------- OTP ----------

    public record OtpRow(UUID id, String codeHash, int attempts, Instant expiresAt,
                         Instant createdAt) {
    }

    public Optional<OtpRow> latestOpenChallenge(UUID companyId, String phone) {
        return jdbc.query("""
                SELECT id, code_hash, attempts, expires_at, created_at
                FROM storefront.otp_challenge
                WHERE company_id = ? AND phone = ? AND consumed_at IS NULL
                ORDER BY created_at DESC LIMIT 1
                """, (rs, n) -> new OtpRow(rs.getObject("id", UUID.class), rs.getString("code_hash"),
                rs.getInt("attempts"), rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("created_at").toInstant()), companyId, phone).stream().findFirst();
    }

    public int challengesSince(UUID companyId, String phone, Instant since) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM storefront.otp_challenge
                WHERE company_id = ? AND phone = ? AND created_at > ?
                """, Integer.class, companyId, phone, Timestamp.from(since));
        return count == null ? 0 : count;
    }

    public void createChallenge(UUID companyId, String phone, String codeHash, Instant expiresAt) {
        // One live code per number: asking again retires the previous one.
        jdbc.update("""
                UPDATE storefront.otp_challenge SET consumed_at = now()
                 WHERE company_id = ? AND phone = ? AND consumed_at IS NULL
                """, companyId, phone);
        jdbc.update("""
                INSERT INTO storefront.otp_challenge (company_id, phone, code_hash, expires_at)
                VALUES (?, ?, ?, ?)
                """, companyId, phone, codeHash, Timestamp.from(expiresAt));
    }

    public void recordFailedAttempt(UUID challengeId) {
        jdbc.update("UPDATE storefront.otp_challenge SET attempts = attempts + 1 WHERE id = ?",
                challengeId);
    }

    public void consumeChallenge(UUID challengeId) {
        jdbc.update("UPDATE storefront.otp_challenge SET consumed_at = now() WHERE id = ?",
                challengeId);
    }

    // ---------- bag ----------

    public record CartRow(UUID itemId, int quantity, String size) {
    }

    public List<CartRow> cart(UUID customerId) {
        return jdbc.query("""
                SELECT item_id, quantity, size FROM storefront.cart_item
                WHERE customer_id = ? ORDER BY position
                """, (rs, n) -> new CartRow(rs.getObject("item_id", UUID.class),
                rs.getInt("quantity"), rs.getString("size")), customerId);
    }

    public void replaceCart(UUID customerId, List<CartRow> rows) {
        jdbc.update("DELETE FROM storefront.cart_item WHERE customer_id = ?", customerId);
        int position = 0;
        for (CartRow row : rows) {
            jdbc.update("""
                    INSERT INTO storefront.cart_item (customer_id, item_id, size, quantity, position)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (customer_id, item_id, size) DO UPDATE
                       SET quantity = storefront.cart_item.quantity + EXCLUDED.quantity
                    """, customerId, row.itemId(), row.size() == null ? "" : row.size(),
                    row.quantity(), position++);
        }
    }

    /** Ids among {@code itemIds} that are real items of the company. */
    public List<UUID> existingItems(UUID companyId, List<UUID> itemIds) {
        if (itemIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(itemIds.size(), "?"));
        Object[] args = new Object[itemIds.size() + 1];
        args[0] = companyId;
        for (int i = 0; i < itemIds.size(); i++) {
            args[i + 1] = itemIds.get(i);
        }
        return jdbc.query("""
                SELECT i.id FROM inventory.jewellery_item i
                JOIN organization.branch b ON b.id = i.current_branch_id
                WHERE b.company_id = ? AND i.id IN (%s)
                """.formatted(placeholders), (rs, n) -> rs.getObject(1, UUID.class), args);
    }

    // ---------- wishlist (customer.customer_wishlist, shared with staff) ----------

    public record WishlistRow(UUID itemId, Instant addedAt) {
    }

    public List<WishlistRow> wishlist(UUID customerId) {
        return jdbc.query("""
                SELECT jewellery_item_id, min(created_at) AS added_at
                FROM customer.customer_wishlist
                WHERE customer_id = ? AND jewellery_item_id IS NOT NULL
                GROUP BY jewellery_item_id ORDER BY added_at DESC
                """, (rs, n) -> new WishlistRow(rs.getObject(1, UUID.class),
                rs.getTimestamp(2).toInstant()), customerId);
    }

    public void removeFromWishlist(UUID customerId, UUID itemId) {
        jdbc.update("""
                DELETE FROM customer.customer_wishlist WHERE customer_id = ? AND jewellery_item_id = ?
                """, customerId, itemId);
    }

    public void addToWishlist(UUID customerId, UUID itemId, Instant addedAt) {
        jdbc.update("""
                INSERT INTO customer.customer_wishlist (id, version, customer_id, jewellery_item_id,
                    added_by, created_at, created_by, updated_at, updated_by)
                VALUES (?, 0, ?, ?, ?, ?, ?, now(), ?)
                """, UUID.randomUUID(), customerId, itemId, ACTOR, Timestamp.from(addedAt), ACTOR,
                ACTOR);
    }

    // ---------- addresses (customer.customer_address) ----------

    public record AddressRow(UUID id, String label, String name, String line1, String city,
                             String postcode, String phone, boolean isDefault) {
    }

    public List<AddressRow> addresses(UUID customerId) {
        return jdbc.query("""
                SELECT id, address_type, contact_name, address_line1, city, postal_code,
                       contact_phone, default_address
                FROM customer.customer_address WHERE customer_id = ?
                ORDER BY default_address DESC, created_at
                """, (rs, n) -> new AddressRow(rs.getObject("id", UUID.class),
                rs.getString("address_type"), rs.getString("contact_name"),
                rs.getString("address_line1"), rs.getString("city"), rs.getString("postal_code"),
                rs.getString("contact_phone"), rs.getBoolean("default_address")), customerId);
    }

    public UUID insertAddress(UUID customerId, AddressRow row) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO customer.customer_address (id, version, customer_id, address_type,
                    address_line1, city, postal_code, contact_name, contact_phone, default_address,
                    created_at, created_by, updated_at, updated_by)
                VALUES (?, 0, ?, ?, ?, ?, ?, ?, ?, false, now(), ?, now(), ?)
                """, id, customerId, row.label(), row.line1(), row.city(), row.postcode(),
                row.name(), row.phone(), ACTOR, ACTOR);
        return id;
    }

    public int updateAddress(UUID customerId, AddressRow row) {
        return jdbc.update("""
                UPDATE customer.customer_address
                   SET address_type = ?, address_line1 = ?, city = ?, postal_code = ?,
                       contact_name = ?, contact_phone = ?, updated_at = now(), updated_by = ?,
                       version = version + 1
                 WHERE customer_id = ? AND id = ?
                """, row.label(), row.line1(), row.city(), row.postcode(), row.name(), row.phone(),
                ACTOR, customerId, row.id());
    }

    public int deleteAddress(UUID customerId, UUID addressId) {
        return jdbc.update("DELETE FROM customer.customer_address WHERE customer_id = ? AND id = ?",
                customerId, addressId);
    }

    public void setDefaultAddress(UUID customerId, UUID addressId) {
        jdbc.update("""
                UPDATE customer.customer_address SET default_address = (id = ?), updated_at = now()
                 WHERE customer_id = ?
                """, addressId, customerId);
    }

    // ---------- payment methods ----------

    public record PaymentRow(UUID id, String kind, String label, String detail, boolean isDefault) {
    }

    public List<PaymentRow> paymentMethods(UUID customerId) {
        return jdbc.query("""
                SELECT id, kind, label, detail, is_default FROM storefront.payment_method
                WHERE customer_id = ? ORDER BY is_default DESC, created_at
                """, (rs, n) -> new PaymentRow(rs.getObject("id", UUID.class), rs.getString("kind"),
                rs.getString("label"), rs.getString("detail"), rs.getBoolean("is_default")),
                customerId);
    }

    public void insertPaymentMethod(UUID customerId, String kind, String label, String detail,
                                    boolean isDefault) {
        jdbc.update("""
                INSERT INTO storefront.payment_method (customer_id, kind, label, detail, is_default)
                VALUES (?, ?, ?, ?, ?)
                """, customerId, kind, label, detail, isDefault);
    }

    public void setDefaultPaymentMethod(UUID customerId, UUID methodId) {
        jdbc.update("UPDATE storefront.payment_method SET is_default = (id = ?) WHERE customer_id = ?",
                methodId, customerId);
    }

    public int deletePaymentMethod(UUID customerId, UUID methodId) {
        return jdbc.update("DELETE FROM storefront.payment_method WHERE customer_id = ? AND id = ?",
                customerId, methodId);
    }

    // ---------- inbox ----------

    public record NotificationRow(UUID id, String kind, String title, String body, String link,
                                  Instant createdAt, boolean read) {
    }

    public List<NotificationRow> notifications(UUID companyId, UUID customerId, Instant since) {
        return jdbc.query("""
                SELECT n.id, n.kind, n.title, n.body, n.link, n.created_at,
                       (r.notification_id IS NOT NULL) AS is_read
                FROM storefront.customer_notification n
                LEFT JOIN storefront.customer_notification_read r
                       ON r.notification_id = n.id AND r.customer_id = ?
                WHERE n.company_id = ? AND (n.customer_id = ? OR n.customer_id IS NULL)
                  AND n.created_at >= ?
                ORDER BY n.created_at DESC LIMIT 100
                """, (rs, n) -> new NotificationRow(rs.getObject("id", UUID.class),
                rs.getString("kind"), rs.getString("title"), rs.getString("body"),
                rs.getString("link"), rs.getTimestamp("created_at").toInstant(),
                rs.getBoolean("is_read")), customerId, companyId, customerId,
                Timestamp.from(since));
    }

    public void markNotificationRead(UUID companyId, UUID customerId, UUID notificationId) {
        jdbc.update("""
                INSERT INTO storefront.customer_notification_read (notification_id, customer_id)
                SELECT n.id, ? FROM storefront.customer_notification n
                 WHERE n.id = ? AND n.company_id = ? AND (n.customer_id = ? OR n.customer_id IS NULL)
                ON CONFLICT DO NOTHING
                """, customerId, notificationId, companyId, customerId);
    }

    public void markAllNotificationsRead(UUID companyId, UUID customerId) {
        jdbc.update("""
                INSERT INTO storefront.customer_notification_read (notification_id, customer_id)
                SELECT n.id, ? FROM storefront.customer_notification n
                 WHERE n.company_id = ? AND (n.customer_id = ? OR n.customer_id IS NULL)
                ON CONFLICT DO NOTHING
                """, customerId, companyId, customerId);
    }

    /** @param customerId null broadcasts to every customer of the company */
    public void notify(UUID companyId, UUID customerId, String kind, String title, String body,
                       String link) {
        jdbc.update("""
                INSERT INTO storefront.customer_notification (company_id, customer_id, kind, title, body, link)
                VALUES (?, ?, ?, ?, ?, ?)
                """, companyId, customerId, kind, title, body, link);
    }

    // ---------- devices ----------

    public void registerDevice(UUID companyId, UUID customerId, String token, String platform) {
        jdbc.update("""
                INSERT INTO storefront.customer_device (token, company_id, customer_id, platform)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (token) DO UPDATE
                   SET company_id = EXCLUDED.company_id, customer_id = EXCLUDED.customer_id,
                       platform = EXCLUDED.platform, last_seen_at = now()
                """, token, companyId, customerId, platform);
    }

    public void unregisterDevice(UUID customerId, String token) {
        jdbc.update("DELETE FROM storefront.customer_device WHERE customer_id = ? AND token = ?",
                customerId, token);
    }

    public List<String> deviceTokens(UUID customerId) {
        return jdbc.query("SELECT token FROM storefront.customer_device WHERE customer_id = ?",
                (rs, n) -> rs.getString(1), customerId);
    }

    public void forgetDeviceToken(String token) {
        jdbc.update("DELETE FROM storefront.customer_device WHERE token = ?", token);
    }

    // ---------- feedback ----------

    public void insertFeedback(UUID companyId, UUID customerId, String ticket, boolean happy,
                               String topic, String message, String email, boolean followUp) {
        jdbc.update("""
                INSERT INTO storefront.feedback (company_id, customer_id, ticket, happy, topic,
                    message, email, follow_up)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, companyId, customerId, ticket, happy, topic, message, email, followUp);
    }

    public void insertRating(UUID companyId, UUID customerId, int stars, String comment) {
        jdbc.update("""
                INSERT INTO storefront.app_rating (company_id, customer_id, stars, comment)
                VALUES (?, ?, ?, ?)
                """, companyId, customerId, stars, comment);
    }
}
