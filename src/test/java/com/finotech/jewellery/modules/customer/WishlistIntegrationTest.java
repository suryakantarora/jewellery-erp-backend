package com.finotech.jewellery.modules.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finotech.jewellery.CommerceFixture;
import com.finotech.jewellery.IntegrationTestBase;
import com.finotech.jewellery.TestSecurity;
import com.finotech.jewellery.modules.customer.api.request.WishlistEntryRequest;
import com.finotech.jewellery.modules.customer.api.response.WishlistEntryResponse;
import com.finotech.jewellery.modules.customer.application.service.WishlistService;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(CommerceFixture.class)
class WishlistIntegrationTest extends IntegrationTestBase {

    @Autowired private CommerceFixture fixture;
    @Autowired private WishlistService wishlistService;

    private CommerceFixture.World world;

    @BeforeEach
    void setUp() {
        TestSecurity.authenticateAsSuperAdmin();
        world = fixture.create(new BigDecimal("2000000"), new BigDecimal("50000"),
                new BigDecimal("2.0"), null);
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("an item is added once, listed with live details, and removable")
    void addListDedupeRemove() {
        UUID itemId = fixture.availableItem(world, new BigDecimal("5.000"));

        WishlistService.AddResult first = wishlistService.add(world.customerId(),
                new WishlistEntryRequest(itemId, null, null, "Liked the setting", world.branchId()));
        assertThat(first.created()).isTrue();
        assertThat(first.entry().itemCode()).isNotBlank();
        assertThat(first.entry().productName()).isEqualTo("Test Ring");
        assertThat(first.entry().productId()).isEqualTo(world.productId());
        assertThat(first.entry().itemStatus()).isEqualTo("AVAILABLE");

        // Same piece again: the existing entry, not a conflict.
        WishlistService.AddResult again = wishlistService.add(world.customerId(),
                new WishlistEntryRequest(itemId, null, null, "again", null));
        assertThat(again.created()).isFalse();
        assertThat(again.entry().id()).isEqualTo(first.entry().id());

        // A product-only wish is fine too.
        WishlistService.AddResult product = wishlistService.add(world.customerId(),
                new WishlistEntryRequest(null, world.productId(), null, null, null));
        assertThat(product.created()).isTrue();
        assertThat(product.entry().itemCode()).isNull();

        List<WishlistEntryResponse> list = wishlistService.list(world.customerId());
        assertThat(list).extracting(WishlistEntryResponse::id)
                .containsExactlyInAnyOrder(first.entry().id(), product.entry().id());

        wishlistService.remove(world.customerId(), first.entry().id());
        assertThat(wishlistService.list(world.customerId()))
                .extracting(WishlistEntryResponse::id).containsExactly(product.entry().id());
    }

    @Test
    @DisplayName("an entry must name something")
    void emptyEntryIsRejected() {
        assertThatThrownBy(() -> wishlistService.add(world.customerId(),
                new WishlistEntryRequest(null, null, null, "nothing", null)))
                .isInstanceOf(ValidationException.class);
    }
}
