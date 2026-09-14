package com.finotech.jewellery.modules.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finotech.jewellery.CommerceFixture;
import com.finotech.jewellery.IntegrationTestBase;
import com.finotech.jewellery.TestSecurity;
import com.finotech.jewellery.modules.customer.api.request.CustomerRequest;
import com.finotech.jewellery.modules.customer.api.response.CustomerResponse;
import com.finotech.jewellery.modules.customer.application.service.CustomerService;
import com.finotech.jewellery.modules.identity.api.request.CreateUserRequest;
import com.finotech.jewellery.modules.identity.api.request.RoleRequest;
import com.finotech.jewellery.modules.identity.api.response.PermissionResponse;
import com.finotech.jewellery.modules.identity.api.response.UserResponse;
import com.finotech.jewellery.modules.identity.application.service.RoleService;
import com.finotech.jewellery.modules.identity.application.service.UserService;
import com.finotech.jewellery.modules.inventory.api.request.CreateItemRequest;
import com.finotech.jewellery.modules.inventory.api.request.TagItemRequest;
import com.finotech.jewellery.modules.inventory.api.response.JewelleryItemResponse;
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
import com.finotech.jewellery.modules.product.api.response.ProductResponse;
import com.finotech.jewellery.modules.product.application.service.ProductService;
import com.finotech.jewellery.modules.product.infrastructure.repository.ProductRepository;
import com.finotech.jewellery.shared.exception.ForbiddenException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.UnauthorizedException;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.security.AuthenticatedUser;
import com.finotech.jewellery.shared.security.JwtService;
import com.finotech.jewellery.shared.security.SecurityUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The company is the tenant boundary. Two companies on one platform must not
 * see each other's catalogue, customers, stock or users, and a grant can never
 * reach into another company's branch.
 */
@AutoConfigureMockMvc
@Import(CommerceFixture.class)
class CompanyTenancyIntegrationTest extends IntegrationTestBase {

    private static final Set<String> VIEW_ALL = Set.of("INVENTORY_VIEW", "PRODUCT_VIEW",
            "CUSTOMER_VIEW", "PRODUCT_CREATE", "USER_MANAGE", "USER_VIEW", "ORGANIZATION_VIEW");

    @Autowired private CommerceFixture fixture;
    @Autowired private OrganizationService organizationService;
    @Autowired private MetalService metalService;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private CustomerService customerService;
    @Autowired private JewelleryItemService itemService;
    @Autowired private UserService userService;
    @Autowired private RoleService roleService;
    @Autowired private JwtService jwtService;
    @Autowired private MockMvc mockMvc;

    private CommerceFixture.World a;
    private UUID companyB;
    private UUID branchB;
    private UUID productB;
    private UUID customerB;
    private UUID itemA;
    private UUID itemB;
    private String tag;

    @BeforeEach
    void twoCompanies() {
        TestSecurity.authenticateAsSuperAdmin();
        tag = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        a = fixture.create(new BigDecimal("1000000"), new BigDecimal("50000"), null, null);
        itemA = fixture.availableItem(a, new BigDecimal("10.000"));

        companyB = organizationService.createCompany(new CompanyRequest(
                "COB" + tag, "Company B", null, null, null, "LAK", null, null, null, null, null)).id();
        branchB = organizationService.createBranch(new BranchRequest(
                companyB, "BRB" + tag, "B Branch", true, null, null, null, null, null, null)).id();
        UUID locationB = organizationService.createLocation(new LocationRequest(
                branchB, null, "LOCB" + tag, "B Showroom", LocationType.SHOWROOM, false, null, null)).id();
        var metalB = metalService.createMetal(new MetalRequest("MTB" + tag, "Gold", "Au", "GRAM",
                null, companyB));
        var purityB = metalService.createPurity(new PurityRequest(
                metalB.id(), "22K", "22 Karat", new BigDecimal("0.916700"), 1));
        productB = productService.createProduct(new ProductRequest(
                "SKUB" + tag, "B Ring", null, a.productTypeId(), null, null, null,
                metalB.id(), purityB.id(), new BigDecimal("10.000"), null, null, null, null, null,
                companyB)).id();
        customerB = customerService.create(new CustomerRequest(
                null, null, "B Customer", null, "+8569" + (System.nanoTime() % 100000000L),
                null, null, null, null, null, null, branchB, null, null)).id();
        var created = itemService.create(new CreateItemRequest(
                null, productB, null, null, new BigDecimal("8.000"), null, null, null, null, null,
                null, null, null, null, null, locationB, null, null));
        itemService.tag(created.id(), new TagItemRequest(null, null, "BC-" + UUID.randomUUID()));
        itemService.approveForStock(created.id());
        itemB = created.id();
    }

    @AfterEach
    void clearAuthentication() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("a user of company A sees none of company B's products, customers or items")
    void listsAreConfinedToTheCallersCompany() {
        TestSecurity.authenticateInCompany(a.companyId(), VIEW_ALL, Set.of(a.branchId()));

        List<UUID> products = productService.searchProducts(null, null, null, null, null,
                Pageable.ofSize(500)).content().stream().map(ProductResponse::id).toList();
        assertThat(products).contains(a.productId()).doesNotContain(productB);

        List<UUID> customers = customerService.search(null, null, null, null, Pageable.ofSize(500))
                .content().stream().map(CustomerResponse::id).toList();
        assertThat(customers).contains(a.customerId()).doesNotContain(customerB);

        List<UUID> items = itemService.search(null, null, null, null, null, null, null, null,
                Pageable.ofSize(500)).content().stream().map(JewelleryItemResponse::id).toList();
        assertThat(items).contains(itemA).doesNotContain(itemB);

        // Even asking for B's branch by name yields nothing rather than B's stock.
        assertThat(itemService.search(null, null, null, null, branchB, null, null, null,
                Pageable.ofSize(50)).content()).isEmpty();
    }

    @Test
    @DisplayName("another company's record is reported as absent, not forbidden")
    void singleRecordsOfAnotherCompanyAreNotFound() {
        TestSecurity.authenticateInCompany(a.companyId(), VIEW_ALL, Set.of(a.branchId()));

        assertThatThrownBy(() -> productService.getProduct(productB)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> customerService.get(customerB)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> itemService.get(itemB)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> organizationService.getCompany(companyB)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> organizationService.getBranch(branchB)).isInstanceOf(NotFoundException.class);

        assertThat(productService.getProduct(a.productId()).id()).isEqualTo(a.productId());
    }

    @Test
    @DisplayName("over HTTP a foreign product is a 404 and a foreign branch is absent from the list")
    void httpSurfaceHidesTheOtherCompany() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "a-user", VIEW_ALL,
                Set.of(a.branchId()), false, a.companyId());
        var token = new UsernamePasswordAuthenticationToken(user, null, user.authorities());

        mockMvc.perform(get("/api/v1/products/{id}", productB).with(authentication(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/v1/products/{id}", a.productId()).with(authentication(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/customers/{id}", customerB).with(authentication(token)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/inventory/items/{id}", itemB).with(authentication(token)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/companies").with(authentication(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(a.companyId().toString()));
        mockMvc.perform(get("/api/v1/branches").param("size", "500").with(authentication(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == '" + branchB + "')]").isEmpty())
                .andExpect(jsonPath("$.data.content[?(@.id == '" + a.branchId() + "')]").isNotEmpty());
    }

    @Test
    @DisplayName("a platform super administrator sees both companies")
    void superAdminSeesEverything() {
        TestSecurity.authenticateAsSuperAdmin();

        List<UUID> products = productService.searchProducts(null, null, null, null, null,
                Pageable.ofSize(500)).content().stream().map(ProductResponse::id).toList();
        assertThat(products).contains(a.productId(), productB);
        assertThat(customerService.get(customerB).id()).isEqualTo(customerB);
        assertThat(itemService.get(itemB).id()).isEqualTo(itemB);
        List<UUID> items = itemService.search(null, null, null, null, null, null, null, null,
                Pageable.ofSize(1000)).content().stream().map(JewelleryItemResponse::id).toList();
        assertThat(items).contains(itemA, itemB);
    }

    @Test
    @DisplayName("creating master data as a company user stamps that company, whatever the request says")
    void createsAreStampedWithTheCallersCompany() {
        TestSecurity.authenticateInCompany(a.companyId(), VIEW_ALL, Set.of(a.branchId()));

        UUID created = productService.createProduct(new ProductRequest(
                "SKUA2" + tag, "A Ring", null, a.productTypeId(), null, null, null,
                a.metalId(), a.purityId(), new BigDecimal("5.000"), null, null, null, null, null,
                null)).id();
        assertThat(productRepository.findById(created).orElseThrow().getCompanyId())
                .isEqualTo(a.companyId());

        assertThatThrownBy(() -> productService.createProduct(new ProductRequest(
                "SKUX" + tag, "Not mine", null, a.productTypeId(), null, null, null,
                null, null, new BigDecimal("5.000"), null, null, null, null, null, companyB)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("another company");

        // The same SKU is free in company B: codes are unique per company.
        TestSecurity.authenticateAsSuperAdmin();
        UUID inB = productService.createProduct(new ProductRequest(
                "SKUA2" + tag, "B copy", null, a.productTypeId(), null, null, null,
                null, null, new BigDecimal("5.000"), null, null, null, null, null, companyB)).id();
        assertThat(productRepository.findById(inB).orElseThrow().getCompanyId()).isEqualTo(companyB);
    }

    @Test
    @DisplayName("a user cannot be granted a branch of another company")
    void branchGrantsStayInsideTheCompany() {
        TestSecurity.authenticateInCompany(a.companyId(), VIEW_ALL, Set.of(a.branchId()));
        UUID roleId = role();

        assertThatThrownBy(() -> userService.create(new CreateUserRequest("ux" + tag,
                "Password123!x", "Cross Company", null, null, null, null, Set.of(roleId),
                Set.of(branchB), null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("another company");

        UserResponse user = userService.create(new CreateUserRequest("ua" + tag,
                "Password123!x", "Same Company", null, null, null, a.branchId(), Set.of(roleId),
                null, null));
        assertThat(user.companyId()).isEqualTo(a.companyId());
        assertThat(user.companyName()).isEqualTo("Test Co");

        // The company travels in the token as the "co" claim.
        AuthenticatedUser parsed = jwtService.parseAccessToken(jwtService.issueAccessToken(
                new AuthenticatedUser(user.id(), user.username(), Set.of(), Set.of(a.branchId()),
                        false, a.companyId())));
        assertThat(parsed.companyId()).isEqualTo(a.companyId());
    }

    @Test
    @DisplayName("requireBranchAccess refuses another company's branch even when a grant names it")
    void branchAccessNeverCrossesCompanies() {
        TestSecurity.authenticateInCompany(a.companyId(), VIEW_ALL, Set.of(a.branchId(), branchB));

        SecurityUtils.requireBranchAccess(a.branchId());
        assertThatThrownBy(() -> SecurityUtils.requireBranchAccess(branchB))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("an ordinary user's token without a company is asked to sign in again")
    void legacyTokensMustReauthenticate() {
        TestSecurity.authenticateInCompany(null, VIEW_ALL, Set.of(a.branchId()));

        assertThatThrownBy(() -> productService.searchProducts(null, null, null, null, null,
                Pageable.ofSize(10)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Sign in again");
    }

    private UUID role() {
        UUID permissionId = roleService.listPermissions().stream()
                .filter(p -> p.code().equals("SALE_VIEW"))
                .map(PermissionResponse::id)
                .findFirst().orElseThrow();
        return roleService.create(new RoleRequest("RL" + tag, "Role " + tag, null,
                Set.of(permissionId))).id();
    }
}
