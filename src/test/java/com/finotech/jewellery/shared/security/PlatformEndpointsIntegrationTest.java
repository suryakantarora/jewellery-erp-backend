package com.finotech.jewellery.shared.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finotech.jewellery.IntegrationTestBase;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The app-platform plumbing: the anonymous version gate, the branch header,
 * and malformed enum parameters answering 400 instead of 500.
 */
@AutoConfigureMockMvc
class PlatformEndpointsIntegrationTest extends IntegrationTestBase {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("the version gate is reachable without a token and computes forceUpdate")
    void versionEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/app/version").param("platform", "ANDROID")
                        .param("current", "0.0.1+3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.platform").value("ANDROID"))
                .andExpect(jsonPath("$.data.minSupported").value("0.1.0"))
                .andExpect(jsonPath("$.data.latest").value("0.1.0"))
                .andExpect(jsonPath("$.data.storeUrl").value(""))
                .andExpect(jsonPath("$.data.forceUpdate").value(true))
                .andExpect(jsonPath("$.data.updateAvailable").value(true));

        mockMvc.perform(get("/api/v1/app/version").param("platform", "IOS")
                        .param("current", "0.1.0+7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.forceUpdate").value(false))
                .andExpect(jsonPath("$.data.updateAvailable").value(false));

        mockMvc.perform(get("/api/v1/app/version").param("platform", "IOS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.forceUpdate").value(false));
    }

    @Test
    @DisplayName("an unknown platform is a 400 that names the accepted values")
    void unknownPlatformIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/app/version").param("platform", "WINDOWS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("platform"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value("Must be one of: ANDROID, IOS"));
    }

    @Test
    @WithMockUser(authorities = "INVENTORY_VIEW")
    @DisplayName("a malformed enum query parameter is a 400 that names the accepted values")
    void malformedEnumParameterIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/items").param("status", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value(containsString("Must be one of: DRAFT, AVAILABLE, RESERVED")));
    }

    @Test
    @DisplayName("X-Branch-Id naming a branch the caller lacks is refused before any controller runs")
    void foreignBranchHeaderIsForbidden() throws Exception {
        UUID myBranch = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "branch-user",
                Set.of("INVENTORY_VIEW"), Set.of(myBranch), false);
        var token = new UsernamePasswordAuthenticationToken(user, null, user.authorities());

        mockMvc.perform(get("/api/v1/inventory/items")
                        .header(BranchContext.HEADER, UUID.randomUUID().toString())
                        .with(authentication(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/inventory/items")
                        .header(BranchContext.HEADER, myBranch.toString())
                        .with(authentication(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/inventory/items")
                        .header(BranchContext.HEADER, "not-a-uuid")
                        .with(authentication(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("the dashboard needs a token but no particular permission")
    void dashboardRequiresAuthenticationOnly() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isUnauthorized());

        UUID branch = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "no-perms",
                Set.of(), Set.of(branch), false);
        var token = new UsernamePasswordAuthenticationToken(user, null, user.authorities());
        // The branch does not exist, so resolution succeeds and the lookup 404s:
        // proof the request got past security into the service.
        mockMvc.perform(get("/api/v1/dashboard/summary").with(authentication(token)))
                .andExpect(status().isNotFound());
    }
}
