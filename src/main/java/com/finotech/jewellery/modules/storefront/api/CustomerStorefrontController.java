package com.finotech.jewellery.modules.storefront.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finotech.jewellery.modules.storefront.application.CustomerAccountService;
import com.finotech.jewellery.modules.storefront.application.CustomerAuthService;
import com.finotech.jewellery.modules.storefront.application.OnlineOrderService;
import com.finotech.jewellery.modules.storefront.infrastructure.CustomerAppStore.AddressRow;
import com.finotech.jewellery.modules.storefront.infrastructure.CustomerAppStore.CartRow;
import com.finotech.jewellery.modules.storefront.infrastructure.CustomerAppStore.NotificationRow;
import com.finotech.jewellery.modules.storefront.infrastructure.CustomerAppStore.PaymentRow;
import com.finotech.jewellery.modules.storefront.infrastructure.CustomerAppStore.WishlistRow;
import com.finotech.jewellery.shared.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in customer's own data. {@code SecurityConfig} admits only the
 * customer principal here, and every service call is scoped to the customer in
 * the token — no endpoint takes a customer id.
 *
 * <p>Shapes mirror the app's models (the app calls a piece a "product", so
 * {@code productId} is a jewellery item id).
 */
@Tag(name = "Storefront (customer)")
@RestController
@RequestMapping("/api/v1/storefront")
@RequiredArgsConstructor
public class CustomerStorefrontController {

    private final CustomerAuthService auth;
    private final CustomerAccountService accounts;
    private final OnlineOrderService orders;

    // ---------- profile ----------

    public record ProfileRequest(String name, String email, String avatar) {
    }

    @GetMapping("/me")
    public ApiResponse<ObjectNode> me() {
        return ApiResponse.ok(auth.toJson(auth.requireCustomer()));
    }

    @PutMapping("/me")
    public ApiResponse<ObjectNode> updateMe(@RequestBody ProfileRequest request) {
        return ApiResponse.ok(auth.updateProfile(request.name(), request.email(), request.avatar()));
    }

    @Operation(summary = "Sign out of every device")
    @PostMapping("/sign-out-everywhere")
    public ApiResponse<Void> signOutEverywhere() {
        auth.signOutEverywhere();
        return ApiResponse.ok(null);
    }

    // ---------- bag ----------

    public record CartItemDto(UUID productId, Integer quantity, String size) {

        static CartItemDto of(CartRow row) {
            return new CartItemDto(row.itemId(), row.quantity(),
                    row.size() == null || row.size().isEmpty() ? null : row.size());
        }

        CartRow toRow() {
            return new CartRow(productId, quantity == null ? 1 : quantity, size);
        }
    }

    @GetMapping("/cart")
    public ApiResponse<List<CartItemDto>> cart() {
        return ApiResponse.ok(accounts.cart().stream().map(CartItemDto::of).toList());
    }

    @Operation(summary = "Replace the bag")
    @PutMapping("/cart")
    public ApiResponse<List<CartItemDto>> saveCart(@RequestBody List<CartItemDto> items) {
        return ApiResponse.ok(accounts.saveCart(items.stream().map(CartItemDto::toRow).toList())
                .stream().map(CartItemDto::of).toList());
    }

    // ---------- wishlist ----------

    public record WishlistItemDto(UUID productId, Instant addedAt) {
    }

    @GetMapping("/wishlist")
    public ApiResponse<List<WishlistItemDto>> wishlist() {
        return ApiResponse.ok(toDtos(accounts.wishlist()));
    }

    @Operation(summary = "Replace the wishlist",
            description = "Shared with staff: pieces a colleague noted for the customer appear here too.")
    @PutMapping("/wishlist")
    public ApiResponse<List<WishlistItemDto>> saveWishlist(@RequestBody List<WishlistItemDto> items) {
        return ApiResponse.ok(toDtos(accounts.saveWishlist(items.stream()
                .map(i -> new WishlistRow(i.productId(), i.addedAt())).toList())));
    }

    private static List<WishlistItemDto> toDtos(List<WishlistRow> rows) {
        return rows.stream().map(r -> new WishlistItemDto(r.itemId(), r.addedAt())).toList();
    }

    // ---------- addresses ----------

    public record AddressDto(UUID id, String label, String name, String line1, String city,
                             String postcode, String phone, boolean isDefault) {

        static AddressDto of(AddressRow row) {
            return new AddressDto(row.id(), row.label() == null ? "other" : row.label().toLowerCase(),
                    orEmpty(row.name()), row.line1(), orEmpty(row.city()), orEmpty(row.postcode()),
                    orEmpty(row.phone()), row.isDefault());
        }

        AddressRow toRow(UUID id) {
            return new AddressRow(id, label, name, line1, city, postcode, phone, isDefault);
        }

        private static String orEmpty(String value) {
            return value == null ? "" : value;
        }
    }

    @GetMapping("/addresses")
    public ApiResponse<List<AddressDto>> addresses() {
        return ApiResponse.ok(accounts.addresses().stream().map(AddressDto::of).toList());
    }

    @PostMapping("/addresses")
    public ResponseEntity<ApiResponse<List<AddressDto>>> addAddress(@RequestBody AddressDto address) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                accounts.saveAddress(address.toRow(null)).stream().map(AddressDto::of).toList()));
    }

    @PutMapping("/addresses/{id}")
    public ApiResponse<List<AddressDto>> updateAddress(@PathVariable UUID id,
                                                       @RequestBody AddressDto address) {
        return ApiResponse.ok(accounts.saveAddress(address.toRow(id)).stream()
                .map(AddressDto::of).toList());
    }

    @DeleteMapping("/addresses/{id}")
    public ApiResponse<List<AddressDto>> deleteAddress(@PathVariable UUID id) {
        return ApiResponse.ok(accounts.deleteAddress(id).stream().map(AddressDto::of).toList());
    }

    @PostMapping("/addresses/{id}/default")
    public ApiResponse<List<AddressDto>> defaultAddress(@PathVariable UUID id) {
        return ApiResponse.ok(accounts.setDefaultAddress(id).stream().map(AddressDto::of).toList());
    }

    // ---------- payment methods ----------

    @GetMapping("/payment-methods")
    public ApiResponse<List<PaymentRow>> paymentMethods() {
        return ApiResponse.ok(accounts.paymentMethods());
    }

    @PostMapping("/payment-methods/{id}/default")
    public ApiResponse<List<PaymentRow>> defaultPaymentMethod(@PathVariable UUID id) {
        return ApiResponse.ok(accounts.setDefaultPaymentMethod(id));
    }

    @DeleteMapping("/payment-methods/{id}")
    public ApiResponse<List<PaymentRow>> removePaymentMethod(@PathVariable UUID id) {
        return ApiResponse.ok(accounts.removePaymentMethod(id));
    }

    // ---------- orders ----------

    public record OrderLineDto(UUID productId, Integer quantity, String size) {
    }

    public record PlaceOrderRequest(List<OrderLineDto> lines, UUID addressId, UUID paymentMethodId,
                                    String offerCode) {
    }

    @GetMapping("/orders")
    public ApiResponse<List<ObjectNode>> orders() {
        return ApiResponse.ok(orders.list());
    }

    @GetMapping("/orders/{number}")
    public ApiResponse<ObjectNode> order(@PathVariable String number) {
        return ApiResponse.ok(orders.get(number));
    }

    @Operation(summary = "Place an order",
            description = "Carries choices, never amounts: the server prices the order. 409 when "
                    + "a piece is no longer available.")
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<ObjectNode>> placeOrder(@RequestBody PlaceOrderRequest request) {
        List<OnlineOrderService.Line> lines = request.lines() == null ? List.of()
                : request.lines().stream().map(l -> new OnlineOrderService.Line(l.productId(),
                        l.quantity() == null ? 1 : l.quantity(), l.size())).toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(orders.place(
                new OnlineOrderService.PlaceOrder(lines, request.addressId(),
                        request.paymentMethodId(), request.offerCode()))));
    }

    // ---------- inbox and devices ----------

    public record NotificationDto(UUID id, String kind, String title, String body, Instant date,
                                  String link, boolean read) {

        static NotificationDto of(NotificationRow row) {
            return new NotificationDto(row.id(), row.kind(), row.title(), row.body(),
                    row.createdAt(), row.link(), row.read());
        }
    }

    public record DeviceRequest(String token, String platform) {
    }

    @GetMapping("/notifications")
    public ApiResponse<List<NotificationDto>> notifications() {
        return ApiResponse.ok(accounts.notifications().stream().map(NotificationDto::of).toList());
    }

    @PostMapping("/notifications/{id}/read")
    public ApiResponse<List<NotificationDto>> markRead(@PathVariable UUID id) {
        return ApiResponse.ok(accounts.markRead(id).stream().map(NotificationDto::of).toList());
    }

    @PostMapping("/notifications/read-all")
    public ApiResponse<List<NotificationDto>> markAllRead() {
        return ApiResponse.ok(accounts.markAllRead().stream().map(NotificationDto::of).toList());
    }

    @PostMapping("/devices")
    public ApiResponse<Void> registerDevice(@RequestBody DeviceRequest request) {
        accounts.registerDevice(request.token(), request.platform());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/devices")
    public ApiResponse<Void> unregisterDevice(@RequestParam String token) {
        accounts.unregisterDevice(token);
        return ApiResponse.ok(null);
    }
}
