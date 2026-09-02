package com.finotech.jewellery.modules.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finotech.jewellery.IntegrationTestBase;
import com.finotech.jewellery.TestSecurity;
import com.finotech.jewellery.modules.inventory.api.request.AssignBinRequest;
import com.finotech.jewellery.modules.inventory.api.request.CreateItemRequest;
import com.finotech.jewellery.modules.inventory.api.request.LinkImageRequest;
import com.finotech.jewellery.modules.inventory.api.request.TagItemRequest;
import com.finotech.jewellery.modules.inventory.api.response.ItemImageResponse;
import com.finotech.jewellery.modules.inventory.application.service.JewelleryItemService;
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
import com.finotech.jewellery.modules.warehouse.api.request.WarehouseRequests;
import com.finotech.jewellery.modules.warehouse.application.service.WarehouseService;
import com.finotech.jewellery.modules.warehouse.domain.enums.BinType;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Bins could be created and listed but never filled — nothing assigned an item
 * to one, so a stock count could record where a piece was *found* with nothing
 * to compare it against.
 */
class ItemBinAndImageIntegrationTest extends IntegrationTestBase {

    @Autowired private JewelleryItemService itemService;
    @Autowired private WarehouseService warehouseService;
    @Autowired private OrganizationService organizationService;
    @Autowired private ProductService productService;
    @Autowired private ProductMasterService productMasterService;
    @Autowired private MetalService metalService;

    private UUID locationId;
    private UUID otherLocationId;
    private UUID productId;

    @BeforeEach
    void setUpMasterData() {
        TestSecurity.authenticateAsSuperAdmin();
        String unique = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        var company = organizationService.createCompany(new CompanyRequest(
                "CO" + unique, "Test Co", null, null, null, "LAK", null, null, null, null, null));
        var branch = organizationService.createBranch(new BranchRequest(
                company.id(), "BR" + unique, "Test Branch", true, null, null, null, null, null, null));
        locationId = organizationService.createLocation(new LocationRequest(
                branch.id(), null, "VLT" + unique, "Vault", LocationType.VAULT, false, null, null)).id();
        otherLocationId = organizationService.createLocation(new LocationRequest(
                branch.id(), null, "SHW" + unique, "Showroom", LocationType.SHOWROOM, false, null,
                null)).id();

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

    @AfterEach
    void clearAuthentication() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("an item can be put in a bin and found by searching that bin")
    void itemCanBeAssignedToABin() {
        UUID itemId = availableItem();
        UUID binId = bin(locationId, "TRAY-1");

        assertThat(itemService.assignBin(itemId, new AssignBinRequest(binId)).binId())
                .isEqualTo(binId);

        // The question the vault screen exists to answer.
        assertThat(itemService.search(null, null, null, null, null, null, null, binId,
                        org.springframework.data.domain.Pageable.ofSize(20)).content())
                .extracting(r -> r.id())
                .containsExactly(itemId);
    }

    @Test
    @DisplayName("a bin belonging to another location is refused")
    void binMustBelongToTheItemsLocation() {
        UUID itemId = availableItem();
        UUID foreignBin = bin(otherLocationId, "TRAY-1");

        assertThatThrownBy(() -> itemService.assignBin(itemId, new AssignBinRequest(foreignBin)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("different location");
    }

    @Test
    @DisplayName("an item can be taken out of its bin")
    void binCanBeCleared() {
        UUID itemId = availableItem();
        itemService.assignBin(itemId, new AssignBinRequest(bin(locationId, "TRAY-1")));

        assertThat(itemService.assignBin(itemId, new AssignBinRequest(null)).binId()).isNull();
    }

    @Test
    @DisplayName("the same bin code is allowed in two locations, but not twice in one")
    void binCodesAreUniquePerLocation() {
        bin(locationId, "TRAY-1");

        // The whole point: every vault wants its own TRAY-1.
        bin(otherLocationId, "TRAY-1");

        assertThatThrownBy(() -> bin(locationId, "TRAY-1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("the first image of a piece becomes its primary one")
    void firstImageIsPrimary() {
        UUID itemId = availableItem();

        ItemImageResponse first = itemService.addImage(itemId,
                new LinkImageRequest("items/a.jpg", "a.jpg", "image/jpeg", 1024L, false, 1));

        assertThat(first.primaryImage())
                .as("nobody should have to declare the only picture primary")
                .isTrue();
        assertThat(itemService.get(itemId).primaryImageKey()).isEqualTo("items/a.jpg");
    }

    @Test
    @DisplayName("a new primary image demotes the previous one")
    void onlyOneImageIsPrimary() {
        UUID itemId = availableItem();
        itemService.addImage(itemId,
                new LinkImageRequest("items/a.jpg", "a.jpg", "image/jpeg", 1L, true, 1));
        itemService.addImage(itemId,
                new LinkImageRequest("items/b.jpg", "b.jpg", "image/jpeg", 1L, true, 2));

        List<ItemImageResponse> images = itemService.images(itemId);
        assertThat(images).hasSize(2);
        assertThat(images).filteredOn(ItemImageResponse::primaryImage)
                .as("the unique index would reject a second primary row")
                .extracting(ItemImageResponse::storageKey)
                .containsExactly("items/b.jpg");
    }

    @Test
    @DisplayName("removing the primary image promotes another, so the header is never empty")
    void removingPrimaryPromotesTheNext() {
        UUID itemId = availableItem();
        ItemImageResponse primary = itemService.addImage(itemId,
                new LinkImageRequest("items/a.jpg", "a.jpg", "image/jpeg", 1L, true, 1));
        itemService.addImage(itemId,
                new LinkImageRequest("items/b.jpg", "b.jpg", "image/jpeg", 1L, false, 2));

        itemService.removeImage(itemId, primary.id());

        assertThat(itemService.get(itemId).primaryImageKey()).isEqualTo("items/b.jpg");
    }

    @Test
    @DisplayName("an image belonging to another item cannot be removed through this one")
    void cannotRemoveAnotherItemsImage() {
        UUID itemId = availableItem();
        UUID otherItemId = availableItem();
        ItemImageResponse image = itemService.addImage(otherItemId,
                new LinkImageRequest("items/x.jpg", "x.jpg", "image/jpeg", 1L, true, 1));

        assertThatThrownBy(() -> itemService.removeImage(itemId, image.id()))
                .isInstanceOf(ValidationException.class);
    }

    private UUID bin(UUID location, String code) {
        return warehouseService.createBin(new WarehouseRequests.BinRequest(
                location, null, code, "Tray", BinType.TRAY, null, null)).id();
    }

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
