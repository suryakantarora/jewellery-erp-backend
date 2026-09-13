package com.finotech.jewellery.modules.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finotech.jewellery.IntegrationTestBase;
import com.finotech.jewellery.TestSecurity;
import com.finotech.jewellery.modules.inventory.api.request.AssignBinRequest;
import com.finotech.jewellery.modules.inventory.api.request.CreateItemRequest;
import com.finotech.jewellery.modules.inventory.api.request.ResolveTagsRequest;
import com.finotech.jewellery.modules.inventory.api.request.TagItemRequest;
import com.finotech.jewellery.modules.inventory.api.response.JewelleryItemResponse;
import com.finotech.jewellery.modules.inventory.api.response.ProductAvailabilityResponse;
import com.finotech.jewellery.modules.inventory.api.response.TagResolutionResponse;
import com.finotech.jewellery.modules.inventory.application.service.JewelleryItemService;
import com.finotech.jewellery.modules.inventory.domain.entity.JewelleryItem;
import com.finotech.jewellery.modules.inventory.infrastructure.repository.JewelleryItemRepository;
import com.finotech.jewellery.modules.metal.api.request.MetalRequest;
import com.finotech.jewellery.modules.metal.api.request.PurityRequest;
import com.finotech.jewellery.modules.metal.application.service.MetalService;
import com.finotech.jewellery.modules.organization.api.request.BranchRequest;
import com.finotech.jewellery.modules.organization.api.request.CompanyRequest;
import com.finotech.jewellery.modules.organization.api.request.LocationRequest;
import com.finotech.jewellery.modules.organization.application.service.OrganizationService;
import com.finotech.jewellery.modules.organization.domain.enums.LocationType;
import com.finotech.jewellery.modules.product.api.request.DesignRequest;
import com.finotech.jewellery.modules.product.api.request.ProductRequest;
import com.finotech.jewellery.modules.product.api.request.ProductTypeRequest;
import com.finotech.jewellery.modules.product.application.service.ProductMasterService;
import com.finotech.jewellery.modules.product.application.service.ProductService;
import com.finotech.jewellery.modules.supplier.api.request.SupplierRequest;
import com.finotech.jewellery.modules.supplier.application.service.SupplierService;
import com.finotech.jewellery.modules.warehouse.api.request.WarehouseRequests;
import com.finotech.jewellery.modules.warehouse.application.service.WarehouseService;
import com.finotech.jewellery.modules.warehouse.domain.enums.BinType;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

/**
 * The mobile list screen needs names, not UUIDs; a scanner gate needs a whole
 * tray resolved in one call; and the sales floor needs to know what the other
 * branches hold. These cover the read paths built for those three questions.
 */
class InventoryLookupIntegrationTest extends IntegrationTestBase {

    @Autowired private JewelleryItemService itemService;
    @Autowired private JewelleryItemRepository itemRepository;
    @Autowired private WarehouseService warehouseService;
    @Autowired private OrganizationService organizationService;
    @Autowired private ProductService productService;
    @Autowired private ProductMasterService productMasterService;
    @Autowired private MetalService metalService;
    @Autowired private SupplierService supplierService;

    private String unique;
    private UUID branchAId;
    private UUID branchBId;
    private UUID locationAId;
    private UUID locationBId;
    private UUID productId;
    private UUID supplierId;

    @BeforeEach
    void setUpMasterData() {
        TestSecurity.authenticateAsSuperAdmin();
        unique = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        var company = organizationService.createCompany(new CompanyRequest(
                "CO" + unique, "Test Co", null, null, null, "LAK", null, null, null, null, null));
        branchAId = organizationService.createBranch(new BranchRequest(
                company.id(), "VTE" + unique, "Vientiane", true, null, null, null, null, null, null)).id();
        branchBId = organizationService.createBranch(new BranchRequest(
                company.id(), "PKZ" + unique, "Pakse", false, null, null, null, null, null, null)).id();
        locationAId = organizationService.createLocation(new LocationRequest(
                branchAId, null, "VLT" + unique, "Main Vault", LocationType.VAULT, false, null, null)).id();
        locationBId = organizationService.createLocation(new LocationRequest(
                branchBId, null, "SHW" + unique, "Pakse Showroom", LocationType.SHOWROOM, false, null,
                null)).id();

        var metal = metalService.createMetal(new MetalRequest("MT" + unique, "Gold", "Au", "GRAM", null));
        var purity = metalService.createPurity(new PurityRequest(
                metal.id(), "22K", "22 Karat", new BigDecimal("0.916700"), 1));
        var type = productMasterService.createProductType(new ProductTypeRequest(
                "PT" + unique, "Ring", null, true, null));
        var design = productService.createDesign(new DesignRequest(
                "DSG" + unique, "Lotus Band", type.id(), null, null, null, null, null));
        productId = productService.createProduct(new ProductRequest(
                "SKU" + unique, "Lotus Ring", design.id(), type.id(), null, null, null,
                metal.id(), purity.id(), new BigDecimal("10.000"), "PER_GRAM",
                new BigDecimal("45000"), null, null, null)).id();
        supplierId = supplierService.create(new SupplierRequest(
                "SUP" + unique, "Golden Hands Workshop", null, null, null, null, null, null,
                null, null, "LAK", null, null, null)).id();
    }

    @AfterEach
    void clearAuthentication() {
        TestSecurity.clear();
    }

    // ---------- display names ----------

    @Test
    @DisplayName("an item carries the names of everything it references")
    void displayNamesAreResolved() {
        UUID binId = warehouseService.createBin(new WarehouseRequests.BinRequest(
                locationAId, null, "TRAY-7", "Tray seven", BinType.TRAY, null, null)).id();
        UUID itemId = availableItem(locationAId, "BC-" + UUID.randomUUID());
        itemService.assignBin(itemId, new AssignBinRequest(binId));

        JewelleryItemResponse item = itemService.get(itemId);

        assertThat(item.productName()).isEqualTo("Lotus Ring");
        assertThat(item.productCode()).isEqualTo("SKU" + unique);
        assertThat(item.designName()).isEqualTo("Lotus Band");
        assertThat(item.metalName()).isEqualTo("Gold");
        assertThat(item.purityCode()).isEqualTo("22K");
        assertThat(item.currentLocationName()).isEqualTo("Main Vault");
        assertThat(item.currentBranchName()).isEqualTo("Vientiane");
        assertThat(item.binCode()).isEqualTo("TRAY-7");
        assertThat(item.supplierName()).isEqualTo("Golden Hands Workshop");
    }

    @Test
    @DisplayName("a search page is labelled the same way as a single item")
    void searchRowsAreLabelled() {
        availableItem(locationBId, "BC-" + UUID.randomUUID());

        List<JewelleryItemResponse> rows = itemService.search(null, productId, null, null,
                branchBId, null, null, null, Pageable.ofSize(20)).content();

        assertThat(rows).isNotEmpty().allSatisfy(row -> {
            assertThat(row.productName()).isEqualTo("Lotus Ring");
            assertThat(row.currentBranchName()).isEqualTo("Pakse");
            assertThat(row.currentLocationName()).isEqualTo("Pakse Showroom");
            assertThat(row.binCode()).as("no bin assigned").isNull();
        });
    }

    // ---------- price range ----------

    @Test
    @DisplayName("the price filter is inclusive at both ends and ignores unpriced items")
    void priceRangeFilter() {
        UUID cheap = priced(availableItem(locationAId, "BC-" + UUID.randomUUID()), "1000000");
        UUID mid = priced(availableItem(locationAId, "BC-" + UUID.randomUUID()), "5000000");
        UUID dear = priced(availableItem(locationAId, "BC-" + UUID.randomUUID()), "9000000");
        UUID unpriced = availableItem(locationAId, "BC-" + UUID.randomUUID());

        assertThat(ids(search(new BigDecimal("1000000"), new BigDecimal("5000000"))))
                .containsExactlyInAnyOrder(cheap, mid);
        assertThat(ids(search(new BigDecimal("5000001"), null)))
                .containsExactly(dear);
        assertThat(ids(search(null, new BigDecimal("999999"))))
                .as("nothing below the cheapest, and null prices never match a bound")
                .isEmpty();
        assertThat(ids(search(null, null)))
                .containsExactlyInAnyOrder(cheap, mid, dear, unpriced);
    }

    @Test
    @DisplayName("a minimum above the maximum is rejected rather than silently empty")
    void invertedPriceRangeIsRejected() {
        assertThatThrownBy(() -> search(new BigDecimal("10"), new BigDecimal("5")))
                .isInstanceOf(ValidationException.class);
    }

    // ---------- bulk tag resolution ----------

    @Test
    @DisplayName("a sweep of tags splits into the items found and the tags nobody knows")
    void bulkTagsSplitResolvedAndUnresolved() {
        String tagA = "RF-" + UUID.randomUUID();
        String tagB = "BC-" + UUID.randomUUID();
        UUID itemA = availableItem(locationAId, tagA);
        UUID itemB = availableItem(locationAId, tagB);
        String itemCodeB = itemService.get(itemB).itemCode();

        TagResolutionResponse result = itemService.resolveTags(new ResolveTagsRequest(List.of(
                tagA, "  " + tagA + "  ", "NOBODY-KNOWS-THIS", itemCodeB.toLowerCase())));

        assertThat(result.resolved())
                .extracting(TagResolutionResponse.ResolvedTag::tag)
                .as("duplicates collapse, whitespace is trimmed, input order kept")
                .containsExactly(tagA, itemCodeB.toLowerCase());
        assertThat(result.resolved())
                .extracting(r -> r.item().id())
                .containsExactly(itemA, itemB);
        assertThat(result.resolved().get(0).item().productName()).isEqualTo("Lotus Ring");
        assertThat(result.unresolved()).containsExactly("NOBODY-KNOWS-THIS");
    }

    // ---------- cross-branch availability ----------

    @Test
    @DisplayName("availability lists every branch the caller may see, zeros included")
    void availabilityForSuperAdminCoversAllBranches() {
        availableItem(locationAId, "BC-" + UUID.randomUUID());
        availableItem(locationAId, "BC-" + UUID.randomUUID());
        draftItem(locationAId);

        ProductAvailabilityResponse availability = itemService.availability(productId);

        assertThat(availability.productName()).isEqualTo("Lotus Ring");
        assertThat(branch(availability, branchAId).available()).isEqualTo(2);
        assertThat(branch(availability, branchAId).total())
                .as("the draft is held stock, just not sellable yet")
                .isEqualTo(3);
        assertThat(branch(availability, branchBId).available()).isZero();
        assertThat(branch(availability, branchBId).total()).isZero();
    }

    @Test
    @DisplayName("a branch-scoped user sees only their own branches")
    void availabilityIsScopedToCallerBranches() {
        availableItem(locationAId, "BC-" + UUID.randomUUID());
        availableItem(locationBId, "BC-" + UUID.randomUUID());

        TestSecurity.authenticateAs(Set.of("INVENTORY_VIEW"), Set.of(branchBId));
        ProductAvailabilityResponse availability = itemService.availability(productId);

        assertThat(availability.branches())
                .extracting(ProductAvailabilityResponse.BranchAvailability::branchId)
                .containsExactly(branchBId);
        assertThat(availability.branches().get(0).branchName()).isEqualTo("Pakse");
        assertThat(availability.branches().get(0).available()).isEqualTo(1);
    }

    // ---------- helpers ----------

    private ProductAvailabilityResponse.BranchAvailability branch(
            ProductAvailabilityResponse availability, UUID branchId) {
        return availability.branches().stream()
                .filter(b -> b.branchId().equals(branchId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("branch missing from availability"));
    }

    private List<JewelleryItemResponse> search(BigDecimal min, BigDecimal max) {
        return itemService.search(null, productId, null, null, null, null, null, null,
                min, max, Pageable.ofSize(50)).content();
    }

    private static List<UUID> ids(List<JewelleryItemResponse> rows) {
        return rows.stream().map(JewelleryItemResponse::id).toList();
    }

    /** Prices are set by the pricing engine in production; the test writes the column directly. */
    private UUID priced(UUID itemId, String price) {
        JewelleryItem item = itemRepository.findById(itemId).orElseThrow();
        item.setCurrentPrice(new BigDecimal(price));
        itemRepository.saveAndFlush(item);
        return itemId;
    }

    private UUID draftItem(UUID locationId) {
        return itemService.create(new CreateItemRequest(
                null, productId, null, null, new BigDecimal("10.000"), null,
                null, null, null, null, new BigDecimal("15000000"), new BigDecimal("450000"),
                null, supplierId, null, locationId, null, null)).id();
    }

    private UUID availableItem(UUID locationId, String tag) {
        UUID itemId = draftItem(locationId);
        if (tag.startsWith("RF-")) {
            itemService.tag(itemId, new TagItemRequest(tag, null, null));
        } else {
            itemService.tag(itemId, new TagItemRequest(null, null, tag));
        }
        itemService.approveForStock(itemId);
        return itemId;
    }
}
