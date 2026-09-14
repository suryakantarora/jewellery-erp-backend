package com.finotech.jewellery;

import com.finotech.jewellery.modules.customer.api.request.CustomerRequest;
import com.finotech.jewellery.modules.customer.application.service.CustomerService;
import com.finotech.jewellery.modules.inventory.api.request.CreateItemRequest;
import com.finotech.jewellery.modules.inventory.api.request.TagItemRequest;
import com.finotech.jewellery.modules.inventory.application.service.JewelleryItemService;
import com.finotech.jewellery.modules.metal.api.request.MetalRequest;
import com.finotech.jewellery.modules.metal.api.request.PublishRateRequest;
import com.finotech.jewellery.modules.metal.api.request.PurityRequest;
import com.finotech.jewellery.modules.metal.application.service.MetalRateService;
import com.finotech.jewellery.modules.metal.application.service.MetalService;
import com.finotech.jewellery.modules.metal.domain.enums.RateType;
import com.finotech.jewellery.modules.organization.api.request.BranchRequest;
import com.finotech.jewellery.modules.organization.api.request.CompanyRequest;
import com.finotech.jewellery.modules.organization.api.request.LocationRequest;
import com.finotech.jewellery.modules.organization.application.service.OrganizationService;
import com.finotech.jewellery.modules.organization.domain.enums.LocationType;
import com.finotech.jewellery.modules.pricing.api.request.DiscountPolicyRequest;
import com.finotech.jewellery.modules.pricing.api.request.MakingChargeRuleRequest;
import com.finotech.jewellery.modules.pricing.api.request.TaxRateRequest;
import com.finotech.jewellery.modules.pricing.application.service.PricingConfigService;
import com.finotech.jewellery.modules.pricing.domain.enums.ChargeType;
import com.finotech.jewellery.modules.product.api.request.ProductRequest;
import com.finotech.jewellery.modules.product.api.request.ProductTypeRequest;
import com.finotech.jewellery.modules.product.application.service.ProductMasterService;
import com.finotech.jewellery.modules.product.application.service.ProductService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;

/**
 * Builds a complete, isolated commercial setup — branch, metal, rate, product,
 * pricing rules and a customer — so each test starts from a known world without
 * depending on data left by another test.
 */
@TestComponent
@RequiredArgsConstructor
public class CommerceFixture {

    private final OrganizationService organizationService;
    private final MetalService metalService;
    private final MetalRateService metalRateService;
    private final ProductService productService;
    private final ProductMasterService productMasterService;
    private final PricingConfigService pricingConfigService;
    private final CustomerService customerService;
    private final JewelleryItemService itemService;

    public record World(UUID companyId, UUID branchId, UUID locationId, UUID metalId,
                        UUID purityId, UUID productTypeId, UUID productId, UUID customerId,
                        BigDecimal ratePerGram) {
    }

    /**
     * @param wastagePercentage wastage on the making-charge rule
     * @param makingPerGram     making charge per gram
     * @param taxPercentage     tax applied on top, or null for none
     */
    public World create(BigDecimal ratePerGram, BigDecimal makingPerGram,
                        BigDecimal wastagePercentage, BigDecimal taxPercentage) {
        String tag = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        var company = organizationService.createCompany(new CompanyRequest(
                "CO" + tag, "Test Co", null, null, null, "LAK", null, null, null, null, null));
        var branch = organizationService.createBranch(new BranchRequest(
                company.id(), "BR" + tag, "Test Branch", true, null, null, null, null, null, null));
        // Ordinary test users belong to this company from here on.
        TestSecurity.defaultCompany(company.id());
        var location = organizationService.createLocation(new LocationRequest(
                branch.id(), null, "LOC" + tag, "Showroom", LocationType.SHOWROOM, false, null, null));

        var metal = metalService.createMetal(new MetalRequest("MT" + tag, "Gold", "Au", "GRAM", null,
                company.id()));
        var purity = metalService.createPurity(new PurityRequest(
                metal.id(), "22K", "22 Karat", new BigDecimal("0.916700"), 1));
        metalRateService.publish(new PublishRateRequest(metal.id(), purity.id(), RateType.SELLING,
                LocalDate.now(), ratePerGram, "LAK", null, null));

        var type = productMasterService.createProductType(new ProductTypeRequest(
                "PT" + tag, "Ring", null, true, null));
        var product = productService.createProduct(new ProductRequest(
                "SKU" + tag, "Test Ring", null, type.id(), null, null, null,
                metal.id(), purity.id(), new BigDecimal("10.000"), null, null, null, null, null,
                company.id()));

        pricingConfigService.createMakingChargeRule(new MakingChargeRuleRequest(
                "MC" + tag, "Making rule", product.id(), null, null, null, null,
                ChargeType.PER_GRAM, makingPerGram, wastagePercentage, null, null,
                LocalDate.now().minusDays(1), null, 10));

        if (taxPercentage != null) {
            pricingConfigService.createTaxRate(new TaxRateRequest(
                    "VAT" + tag, "VAT", taxPercentage, branch.id(), null, false,
                    LocalDate.now().minusDays(1), null));
        }

        pricingConfigService.createDiscountPolicy(new DiscountPolicyRequest(
                "DP" + tag, "Branch policy", branch.id(), new BigDecimal("5.0"),
                new BigDecimal("20.0"), true, false, LocalDate.now().minusDays(1), null));

        var customer = customerService.create(new CustomerRequest(
                null, null, "Test Customer", null, "+8562" + System.nanoTime() % 100000000L,
                null, null, null, null, null, null, branch.id(), null, company.id()));

        return new World(company.id(), branch.id(), location.id(), metal.id(), purity.id(), type.id(),
                product.id(), customer.id(), ratePerGram);
    }

    /** Creates one item and releases it into sellable stock. */
    public UUID availableItem(World world, BigDecimal grossWeight) {
        var created = itemService.create(new CreateItemRequest(
                null, world.productId(), null, null, grossWeight, null, null, null, null, null,
                null, null, null, null, null, world.locationId(), null, null));
        itemService.tag(created.id(), new TagItemRequest(null, null,
                "BC-" + UUID.randomUUID()));
        itemService.approveForStock(created.id());
        return created.id();
    }
}
