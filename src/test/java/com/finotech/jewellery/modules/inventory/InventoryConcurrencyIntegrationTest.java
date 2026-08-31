package com.finotech.jewellery.modules.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finotech.jewellery.IntegrationTestBase;
import com.finotech.jewellery.modules.inventory.api.request.CreateItemRequest;
import com.finotech.jewellery.modules.inventory.api.request.TagItemRequest;
import com.finotech.jewellery.modules.inventory.application.service.JewelleryItemService;
import com.finotech.jewellery.modules.inventory.domain.enums.ItemStatus;
import com.finotech.jewellery.modules.metal.api.request.MetalRequest;
import com.finotech.jewellery.modules.metal.api.request.PurityRequest;
import com.finotech.jewellery.modules.metal.application.service.MetalService;
import com.finotech.jewellery.modules.organization.api.request.BranchRequest;
import com.finotech.jewellery.modules.organization.api.request.CompanyRequest;
import com.finotech.jewellery.modules.organization.api.request.LocationRequest;
import com.finotech.jewellery.modules.organization.application.service.OrganizationService;
import com.finotech.jewellery.modules.organization.domain.enums.LocationType;
import com.finotech.jewellery.modules.product.api.request.ProductRequest;
import com.finotech.jewellery.modules.product.api.request.ProductTypeRequest;
import com.finotech.jewellery.modules.product.application.service.ProductMasterService;
import com.finotech.jewellery.modules.product.application.service.ProductService;
import com.finotech.jewellery.shared.exception.ConflictException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The rules from section 12 that matter most: an item may be sold once, and a
 * reservation for one customer blocks a sale to another.
 */
class InventoryConcurrencyIntegrationTest extends IntegrationTestBase {

    @Autowired private JewelleryItemService itemService;
    @Autowired private OrganizationService organizationService;
    @Autowired private ProductService productService;
    @Autowired private ProductMasterService productMasterService;
    @Autowired private MetalService metalService;

    private UUID locationId;
    private UUID productId;

    @BeforeEach
    void setUpMasterData() {
        String unique = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        var company = organizationService.createCompany(new CompanyRequest(
                "CO" + unique, "Test Co", null, null, null, "LAK", null, null, null, null, null));
        var branch = organizationService.createBranch(new BranchRequest(
                company.id(), "BR" + unique, "Test Branch", true, null, null, null, null, null, null));
        locationId = organizationService.createLocation(new LocationRequest(
                branch.id(), null, "LOC" + unique, "Showroom", LocationType.SHOWROOM, false, null, null)).id();

        var metal = metalService.createMetal(new MetalRequest("MT" + unique, "Gold", "Au", "GRAM", null));
        var purity = metalService.createPurity(new PurityRequest(
                metal.id(), "22K", "22 Karat", new BigDecimal("0.916700"), 1));
        var type = productMasterService.createProductType(new ProductTypeRequest(
                "PT" + unique, "Ring", null, true, null));
        productId = productService.createProduct(new ProductRequest(
                "SKU" + unique, "Test Ring", null, type.id(), null, null, null,
                metal.id(), purity.id(), new BigDecimal("10.000"), "PER_GRAM",
                new BigDecimal("45000"), null, null, null)).id();
    }

    @Test
    @DisplayName("an item can only be sold once")
    void anItemCannotBeSoldTwice() {
        UUID itemId = availableItem();
        UUID firstCustomer = UUID.randomUUID();
        UUID secondCustomer = UUID.randomUUID();

        itemService.markSold(itemId, firstCustomer, UUID.randomUUID(), new BigDecimal("19000000"));

        assertThatThrownBy(() ->
                itemService.markSold(itemId, secondCustomer, UUID.randomUUID(), new BigDecimal("19000000")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cannot be sold");

        assertThat(itemService.get(itemId).status()).isEqualTo(ItemStatus.SOLD);
        assertThat(itemService.get(itemId).ownerCustomerId()).isEqualTo(firstCustomer);
    }

    @Test
    @DisplayName("an item reserved for one customer cannot be sold to another")
    void reservedItemCannotBeSoldToAnotherCustomer() {
        UUID itemId = availableItem();
        UUID holder = UUID.randomUUID();
        itemService.reserve(itemId, holder, 24);

        assertThatThrownBy(() ->
                itemService.markSold(itemId, UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("reserved for another customer");

        // The customer holding the reservation can still complete the purchase.
        itemService.markSold(itemId, holder, UUID.randomUUID(), new BigDecimal("19000000"));
        assertThat(itemService.get(itemId).status()).isEqualTo(ItemStatus.SOLD);
    }

    @Test
    @DisplayName("an item already reserved cannot be reserved again")
    void doubleReservationIsRejected() {
        UUID itemId = availableItem();
        itemService.reserve(itemId, UUID.randomUUID(), 24);

        assertThatThrownBy(() -> itemService.reserve(itemId, UUID.randomUUID(), 24))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cannot be reserved");
    }

    /** Creates, tags and releases one item so it is AVAILABLE for sale. */
    private UUID availableItem() {
        String tag = UUID.randomUUID().toString();
        var created = itemService.create(new CreateItemRequest(
                null, productId, null, null, new BigDecimal("10.000"), null,
                null, null, null, null, new BigDecimal("15000000"), new BigDecimal("450000"),
                null, null, null, locationId, null, null));
        itemService.tag(created.id(), new TagItemRequest(null, null, "BC-" + tag));
        itemService.approveForStock(created.id());
        return created.id();
    }
}
