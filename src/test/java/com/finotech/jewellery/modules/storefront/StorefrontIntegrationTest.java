package com.finotech.jewellery.modules.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finotech.jewellery.CommerceFixture;
import com.finotech.jewellery.IntegrationTestBase;
import com.finotech.jewellery.TestSecurity;
import com.finotech.jewellery.modules.storefront.application.OnlineOrderService;
import com.finotech.jewellery.modules.storefront.application.StorefrontCatalogueService;
import com.finotech.jewellery.modules.storefront.application.TenantService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The customer app's journey against the real stack: branding, the priced
 * catalogue, OTP sign-in, the bag, an order — and the walls around it.
 */
@AutoConfigureMockMvc
@Import(CommerceFixture.class)
@TestPropertySource(properties = {
        "jewellery.storefront.otp.fixed-code=123456",
        "jewellery.storefront.otp.resend-seconds=0",
        "jewellery.storefront.catalogue-cache-seconds=0"})
class StorefrontIntegrationTest extends IntegrationTestBase {

    private static final String TENANT = "X-Tenant-Key";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private CommerceFixture fixture;
    @Autowired private TenantService tenants;
    @Autowired private StorefrontCatalogueService catalogue;
    @Autowired private OnlineOrderService onlineOrders;

    private CommerceFixture.World world;
    private UUID itemId;
    private String tenantKey;

    @BeforeEach
    void setUp() throws Exception {
        TestSecurity.authenticateAsSuperAdmin();
        world = fixture.create(new BigDecimal("1000000"), new BigDecimal("50000"),
                new BigDecimal("2.0"), null);
        itemId = fixture.availableItem(world, new BigDecimal("10.000"));
        tenantKey = "t-" + UUID.randomUUID().toString().substring(0, 8);
        tenants.save(world.companyId(), tenantKey, json.readTree("""
                {"brandMark": "TST", "taxRate": 0.10, "deliveryFee": 25000,
                 "freeDeliveryThreshold": 999999999999, "currency": {"decimals": 0}}
                """), true);
        tenants.putContent(world.companyId(), "offers", json.readTree("""
                [{"id": "O1", "code": "TENOFF", "kind": "percent", "value": 10, "minSubtotal": 0}]
                """), "test");
        TestSecurity.clear();
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("branding falls back to the company record and carries the key")
    void tenantConfig() throws Exception {
        mockMvc.perform(get("/api/v1/public/tenant/" + tenantKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value(tenantKey))
                .andExpect(jsonPath("$.data.brandName").value("Test Co"))
                .andExpect(jsonPath("$.data.currency.code").value("LAK"))
                .andExpect(jsonPath("$.data.currency.decimals").value(0))
                .andExpect(jsonPath("$.data.taxRate").value(0.10));

        mockMvc.perform(get("/api/v1/public/tenant/no-such-shop"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the public catalogue is priced, scoped to the tenant, and leaks no cost")
    void publicCatalogue() throws Exception {
        String body = mockMvc.perform(get("/api/v1/public/catalogue/items").header(TENANT, tenantKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(itemId.toString()))
                .andExpect(jsonPath("$.data[0].productName").value("Test Ring"))
                .andExpect(jsonPath("$.data[0].retail.inStock").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).at("/data/0/price").decimalValue()).isPositive();
        assertThat(body).doesNotContainIgnoringCase("cost").doesNotContainIgnoringCase("supplier");

        mockMvc.perform(get("/api/v1/public/catalogue/items"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("rates, content, categories and reviews are readable anonymously")
    void publicContent() throws Exception {
        mockMvc.perform(get("/api/v1/public/gold-rates").header(TENANT, tenantKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rates[0].purityCode").value("22K"))
                .andExpect(jsonPath("$.data.rates[0].rates.LAK").value(1000000))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());
        mockMvc.perform(get("/api/v1/public/content/offers").header(TENANT, tenantKey))
                .andExpect(jsonPath("$.data[0].code").value("TENOFF"));
        mockMvc.perform(get("/api/v1/public/content/about").header(TENANT, tenantKey))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/public/content/secrets").header(TENANT, tenantKey))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/public/categories").header(TENANT, tenantKey))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/public/reviews").header(TENANT, tenantKey)
                        .param("productId", itemId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("only catalogue artwork is served publicly")
    void publicFilesAreFenced() throws Exception {
        mockMvc.perform(get("/api/v1/public/files").param("key", "general/2026-09-02/kyc.jpg"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/public/files").param("key", "storefront/x.pdf"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a customer signs in with an OTP, fills a bag and places an order the server prices")
    void signInAndOrder() throws Exception {
        // anonymous(): the fixture's super administrator lingers in the test security
        // context, which MockMvc would otherwise replay into the request.
        mockMvc.perform(get("/api/v1/storefront/me").with(anonymous()))
                .andExpect(status().isUnauthorized());

        String phone = "+856 20 " + (10000000 + (System.nanoTime() % 89999999));
        otpRequest(phone).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300));
        otpVerify(phone, "000000").andExpect(status().isUnauthorized());
        JsonNode session = data(otpVerify(phone, "123456").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.name").value(""))
                .andReturn().getResponse().getContentAsString());
        String token = session.get("token").asText();

        // A customer token is not a staff token, anywhere.
        mockMvc.perform(get("/api/v1/catalogue/items").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(as(put("/api/v1/storefront/me"), token)
                        .content("{\"name\": \"Noy\", \"email\": \"noy@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Noy"));

        mockMvc.perform(as(put("/api/v1/storefront/cart"), token).content(
                        "[{\"productId\": \"%s\", \"quantity\": 1, \"size\": \"12\"},"
                                .formatted(itemId)
                                + "{\"productId\": \"%s\", \"quantity\": 1}]".formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].size").value("12"));

        mockMvc.perform(as(put("/api/v1/storefront/wishlist"), token).content(
                        "[{\"productId\": \"%s\", \"addedAt\": \"2026-09-01T10:00:00Z\"}]"
                                .formatted(itemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        String addressId = data(mockMvc.perform(as(post("/api/v1/storefront/addresses"), token)
                        .content("""
                                {"label": "home", "name": "Noy", "line1": "48 Setthathirath Rd",
                                 "city": "Vientiane", "postcode": "01000", "phone": "+8562055550142"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asText();
        String paymentId = data(mockMvc.perform(as(get("/api/v1/storefront/payment-methods"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].kind").value("cod"))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asText();

        BigDecimal price = catalogue.item(world.companyId(), itemId).orElseThrow().price();
        BigDecimal discount = price.multiply(new BigDecimal("0.10")).setScale(0, java.math.RoundingMode.HALF_UP);
        BigDecimal tax = price.subtract(discount).multiply(new BigDecimal("0.10"))
                .setScale(0, java.math.RoundingMode.HALF_UP);
        BigDecimal total = price.subtract(discount).add(new BigDecimal("25000")).add(tax);

        String orderBody = """
                {"lines": [{"productId": "%s", "quantity": 1, "size": "12"}],
                 "addressId": "%s", "paymentMethodId": "%s", "offerCode": "tenoff"}
                """.formatted(itemId, addressId, paymentId);
        JsonNode order = data(mockMvc.perform(as(post("/api/v1/storefront/orders"), token)
                        .content(orderBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("placed"))
                .andExpect(jsonPath("$.data.items[0].size").value("12"))
                .andExpect(jsonPath("$.data.address").value("48 Setthathirath Rd, Vientiane 01000"))
                .andReturn().getResponse().getContentAsString());
        assertThat(order.get("id").asText()).startsWith("TST-");
        assertThat(order.get("total").decimalValue()).isEqualByComparingTo(total);
        assertThat(order.get("shipping").decimalValue()).isEqualByComparingTo("25000");

        // The piece is promised: gone from the window, and not orderable twice.
        mockMvc.perform(get("/api/v1/public/catalogue/items").header(TENANT, tenantKey))
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(as(post("/api/v1/storefront/orders"), token).content(orderBody))
                .andExpect(status().isConflict());
        mockMvc.perform(as(get("/api/v1/storefront/cart"), token))
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(as(get("/api/v1/storefront/notifications"), token))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].kind").value("order"))
                .andExpect(jsonPath("$.data[0].read").value(false));
        mockMvc.perform(as(post("/api/v1/storefront/notifications/read-all"), token))
                .andExpect(jsonPath("$.data[0].read").value(true));

        // Staff move the order along; the customer hears about it, and a
        // cancellation puts the piece back in the window.
        String number = order.get("id").asText();
        onlineOrders.updateStatus(world.companyId(), number, "shipped", "Lao Express",
                "LX-1", null, "staff");
        mockMvc.perform(as(get("/api/v1/storefront/orders/" + number), token))
                .andExpect(jsonPath("$.data.status").value("shipped"))
                .andExpect(jsonPath("$.data.courier").value("Lao Express"));
        mockMvc.perform(as(get("/api/v1/storefront/notifications"), token))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].title").value("Out for delivery"));
        onlineOrders.updateStatus(world.companyId(), number, "cancelled", null, null, null, "staff");
        mockMvc.perform(get("/api/v1/public/catalogue/items").header(TENANT, tenantKey))
                .andExpect(jsonPath("$.data.length()").value(1));

        // Signing out everywhere kills the token.
        mockMvc.perform(as(post("/api/v1/storefront/sign-out-everywhere"), token))
                .andExpect(status().isOk());
        mockMvc.perform(as(get("/api/v1/storefront/me"), token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("guests can send feedback")
    void guestFeedback() throws Exception {
        mockMvc.perform(post("/api/v1/public/feedback").header(TENANT, tenantKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"happy\": true, \"topic\": \"app\", \"message\": \"Lovely\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticket").isNotEmpty());
    }

    private org.springframework.test.web.servlet.ResultActions otpRequest(String phone)
            throws Exception {
        return mockMvc.perform(post("/api/v1/customer-auth/otp/request").header(TENANT, tenantKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\": \"%s\"}".formatted(phone)));
    }

    private org.springframework.test.web.servlet.ResultActions otpVerify(String phone, String code)
            throws Exception {
        return mockMvc.perform(post("/api/v1/customer-auth/otp/verify").header(TENANT, tenantKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\": \"%s\", \"code\": \"%s\"}".formatted(phone, code)));
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request,
                                                    String token) {
        return request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode data(String body) throws Exception {
        return json.readTree(body).get("data");
    }
}
