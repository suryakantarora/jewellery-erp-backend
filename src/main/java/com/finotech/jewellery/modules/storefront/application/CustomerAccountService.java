package com.finotech.jewellery.modules.storefront.application;

import com.finotech.jewellery.modules.storefront.infrastructure.CustomerAppStore;
import com.finotech.jewellery.modules.storefront.infrastructure.CustomerAppStore.AddressRow;
import com.finotech.jewellery.modules.storefront.infrastructure.CustomerAppStore.CartRow;
import com.finotech.jewellery.modules.storefront.infrastructure.CustomerAppStore.NotificationRow;
import com.finotech.jewellery.modules.storefront.infrastructure.CustomerAppStore.PaymentRow;
import com.finotech.jewellery.modules.storefront.infrastructure.CustomerAppStore.WishlistRow;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.utils.CodeGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * What a signed-in customer keeps on the server. The app saves its bag and
 * wishlist as whole lists, so both are replaced rather than patched.
 */
@Service
@RequiredArgsConstructor
public class CustomerAccountService {

    private static final int MAX_BAG_LINES = 50;
    private static final int MAX_WISHLIST = 200;
    private static final Set<String> LABELS = Set.of("home", "work", "other");
    private static final Set<String> PLATFORMS = Set.of("ANDROID", "IOS", "WEB");
    private static final Duration INBOX_WINDOW = Duration.ofDays(90);

    private final CustomerAppStore store;
    private final CustomerAuthService auth;

    // ---------- bag ----------

    public List<CartRow> cart() {
        return store.cart(auth.requireCustomer().id());
    }

    @Transactional
    public List<CartRow> saveCart(List<CartRow> rows) {
        UUID customerId = auth.requireCustomer().id();
        if (rows.size() > MAX_BAG_LINES) {
            throw new ValidationException("A bag holds at most " + MAX_BAG_LINES + " lines");
        }
        rows.forEach(row -> {
            if (row.itemId() == null || row.quantity() < 1 || row.quantity() > 99) {
                throw new ValidationException("Each bag line needs a productId and a quantity of 1-99");
            }
        });
        // Ids of another company, or of nothing at all, are dropped rather than
        // refused: the bag is saved from a device that may be holding stale data.
        Set<UUID> known = new HashSet<>(store.existingItems(auth.principal().companyId(),
                rows.stream().map(CartRow::itemId).distinct().toList()));
        store.replaceCart(customerId, rows.stream().filter(r -> known.contains(r.itemId())).toList());
        return store.cart(customerId);
    }

    // ---------- wishlist ----------

    public List<WishlistRow> wishlist() {
        return store.wishlist(auth.requireCustomer().id());
    }

    @Transactional
    public List<WishlistRow> saveWishlist(List<WishlistRow> wanted) {
        UUID customerId = auth.requireCustomer().id();
        if (wanted.size() > MAX_WISHLIST) {
            throw new ValidationException("A wishlist holds at most " + MAX_WISHLIST + " pieces");
        }
        Set<UUID> known = new HashSet<>(store.existingItems(auth.principal().companyId(),
                wanted.stream().map(WishlistRow::itemId).distinct().toList()));
        Set<UUID> keep = new HashSet<>();
        wanted.stream().filter(w -> known.contains(w.itemId())).forEach(w -> keep.add(w.itemId()));

        Set<UUID> existing = new HashSet<>();
        for (WishlistRow row : store.wishlist(customerId)) {
            existing.add(row.itemId());
            if (!keep.contains(row.itemId())) {
                store.removeFromWishlist(customerId, row.itemId());
            }
        }
        for (WishlistRow row : wanted) {
            if (keep.contains(row.itemId()) && existing.add(row.itemId())) {
                store.addToWishlist(customerId, row.itemId(),
                        row.addedAt() == null ? Instant.now() : row.addedAt());
            }
        }
        return store.wishlist(customerId);
    }

    // ---------- addresses ----------

    public List<AddressRow> addresses() {
        return store.addresses(auth.requireCustomer().id());
    }

    /** Insert when {@code id} is null, else update. The first address becomes the default. */
    @Transactional
    public List<AddressRow> saveAddress(AddressRow request) {
        UUID customerId = auth.requireCustomer().id();
        AddressRow row = validated(request);
        UUID id = row.id();
        if (id == null) {
            id = store.insertAddress(customerId, row);
        } else if (store.updateAddress(customerId, row) == 0) {
            throw new NotFoundException("Address not found");
        }
        List<AddressRow> all = store.addresses(customerId);
        if (row.isDefault() || all.stream().noneMatch(AddressRow::isDefault)) {
            store.setDefaultAddress(customerId, id);
            all = store.addresses(customerId);
        }
        return all;
    }

    @Transactional
    public List<AddressRow> deleteAddress(UUID addressId) {
        UUID customerId = auth.requireCustomer().id();
        if (store.deleteAddress(customerId, addressId) == 0) {
            throw new NotFoundException("Address not found");
        }
        List<AddressRow> all = store.addresses(customerId);
        if (!all.isEmpty() && all.stream().noneMatch(AddressRow::isDefault)) {
            store.setDefaultAddress(customerId, all.get(0).id());
            all = store.addresses(customerId);
        }
        return all;
    }

    @Transactional
    public List<AddressRow> setDefaultAddress(UUID addressId) {
        UUID customerId = auth.requireCustomer().id();
        if (store.addresses(customerId).stream().noneMatch(a -> a.id().equals(addressId))) {
            throw new NotFoundException("Address not found");
        }
        store.setDefaultAddress(customerId, addressId);
        return store.addresses(customerId);
    }

    private static AddressRow validated(AddressRow row) {
        if (!StringUtils.hasText(row.line1()) || !StringUtils.hasText(row.name())
                || !StringUtils.hasText(row.phone())) {
            throw new ValidationException("An address needs a name, a phone number and a first line");
        }
        String label = row.label() == null ? "other" : row.label().toLowerCase(Locale.ROOT);
        return new AddressRow(row.id(), LABELS.contains(label) ? label : "other",
                clip(row.name(), 200), clip(row.line1(), 255), clip(row.city(), 100),
                clip(row.postcode(), 20), clip(row.phone(), 30), row.isDefault());
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    // ---------- payment methods ----------

    /**
     * Cash on delivery is always on offer, so a customer who has never saved
     * anything can still check out.
     */
    @Transactional
    public List<PaymentRow> paymentMethods() {
        UUID customerId = auth.requireCustomer().id();
        List<PaymentRow> methods = store.paymentMethods(customerId);
        if (methods.isEmpty()) {
            store.insertPaymentMethod(customerId, "cod", "Cash on delivery", "", true);
            methods = store.paymentMethods(customerId);
        }
        return methods;
    }

    @Transactional
    public List<PaymentRow> setDefaultPaymentMethod(UUID methodId) {
        UUID customerId = auth.requireCustomer().id();
        if (store.paymentMethods(customerId).stream().noneMatch(m -> m.id().equals(methodId))) {
            throw new NotFoundException("Payment method not found");
        }
        store.setDefaultPaymentMethod(customerId, methodId);
        return store.paymentMethods(customerId);
    }

    @Transactional
    public List<PaymentRow> removePaymentMethod(UUID methodId) {
        UUID customerId = auth.requireCustomer().id();
        if (store.deletePaymentMethod(customerId, methodId) == 0) {
            throw new NotFoundException("Payment method not found");
        }
        List<PaymentRow> methods = store.paymentMethods(customerId);
        if (!methods.isEmpty() && methods.stream().noneMatch(PaymentRow::isDefault)) {
            store.setDefaultPaymentMethod(customerId, methods.get(0).id());
        }
        return paymentMethods();
    }

    // ---------- inbox and devices ----------

    public List<NotificationRow> notifications() {
        return store.notifications(auth.principal().companyId(), auth.requireCustomer().id(),
                Instant.now().minus(INBOX_WINDOW));
    }

    @Transactional
    public List<NotificationRow> markRead(UUID notificationId) {
        store.markNotificationRead(auth.principal().companyId(), auth.requireCustomer().id(),
                notificationId);
        return notifications();
    }

    @Transactional
    public List<NotificationRow> markAllRead() {
        store.markAllNotificationsRead(auth.principal().companyId(), auth.requireCustomer().id());
        return notifications();
    }

    @Transactional
    public void registerDevice(String token, String platform) {
        if (!StringUtils.hasText(token) || token.length() > 512) {
            throw new ValidationException("A device token of up to 512 characters is required");
        }
        String normalized = platform == null ? "" : platform.trim().toUpperCase(Locale.ROOT);
        if (!PLATFORMS.contains(normalized)) {
            throw new ValidationException("platform must be one of " + PLATFORMS);
        }
        store.registerDevice(auth.principal().companyId(), auth.requireCustomer().id(),
                token.trim(), normalized);
    }

    @Transactional
    public void unregisterDevice(String token) {
        store.unregisterDevice(auth.requireCustomer().id(), token);
    }

    // ---------- feedback (guests too) ----------

    @Transactional
    public String submitFeedback(UUID companyId, UUID customerIdOrNull, boolean happy, String topic,
                                 String message, String email, boolean followUp) {
        if (!StringUtils.hasText(message) || message.length() > 2000) {
            throw new ValidationException("A message of up to 2000 characters is required");
        }
        String ticket = "FB-" + CodeGenerator.random(6);
        store.insertFeedback(companyId, customerIdOrNull, ticket, happy,
                StringUtils.hasText(topic) ? clip(topic, 60) : "general", message.trim(),
                StringUtils.hasText(email) ? clip(email, 150) : null, followUp);
        return ticket;
    }

    @Transactional
    public void rate(UUID companyId, UUID customerIdOrNull, int stars, String comment) {
        if (stars < 1 || stars > 5) {
            throw new ValidationException("stars must be between 1 and 5");
        }
        store.insertRating(companyId, customerIdOrNull, stars, clip(comment, 1000));
    }
}
