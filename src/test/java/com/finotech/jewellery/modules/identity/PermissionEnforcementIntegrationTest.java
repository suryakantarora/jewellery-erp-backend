package com.finotech.jewellery.modules.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finotech.jewellery.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Authorization is enforced per endpoint on permission authorities, not on role
 * names, so a role's permission set can change without touching controllers.
 */
@AutoConfigureMockMvc
class PermissionEnforcementIntegrationTest extends IntegrationTestBase {

    private static final String VALID_ITEM_BODY = """
            {
              "productId": "00000000-0000-0000-0000-000000000001",
              "grossWeight": 10.5,
              "locationId": "00000000-0000-0000-0000-000000000002"
            }
            """;

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("an anonymous request is rejected with 401 and the standard error shape")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/items"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(authorities = "PRODUCT_VIEW")
    @DisplayName("a user without INVENTORY_VIEW cannot read stock")
    void wrongPermissionIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/items"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(authorities = "INVENTORY_VIEW")
    @DisplayName("a user with INVENTORY_VIEW can read stock but cannot create items")
    void viewPermissionDoesNotGrantWrite() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/items")).andExpect(status().isOk());

        // A well-formed body, so the request reaches the authorization check
        // rather than failing bean validation first.
        mockMvc.perform(post("/api/v1/inventory/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ITEM_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "INVENTORY_TRANSFER")
    @DisplayName("raising a transfer does not grant the right to approve it")
    void transferPermissionDoesNotGrantApproval() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/transfers/{id}/approve",
                        "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "INVENTORY_CREATE")
    @DisplayName("request validation runs once the caller is authorized")
    void validationErrorsAreReported() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grossWeight\": -5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    @DisplayName("swagger and health are reachable without a token")
    void publicEndpointsAreOpen() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SALE_CREATE")
    @DisplayName("a malformed enum inside a nested body element is a 400 naming the line, not a 500")
    void malformedNestedEnumIsAValidationError() throws Exception {
        String body = """
                {
                  "customerId": "00000000-0000-0000-0000-000000000001",
                  "branchId": "00000000-0000-0000-0000-000000000002",
                  "lines": [
                    {"jewelleryItemId": "00000000-0000-0000-0000-000000000003", "discountType": "PERCENTAGE"},
                    {"jewelleryItemId": "00000000-0000-0000-0000-000000000004", "discountType": "BOGUS"}
                  ]
                }
                """;
        mockMvc.perform(post("/api/v1/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lines[1].discountType"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value(org.hamcrest.Matchers.containsString("PERCENTAGE")));
    }
}
