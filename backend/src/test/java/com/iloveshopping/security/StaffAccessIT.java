// Least privilege on the staff API: each role reaches only its areas, and only after a second factor.
package com.iloveshopping.security;

import com.iloveshopping.support.CatalogTestSupport;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The security chain decides before any controller runs, so this matrix holds for every endpoint under
 * each prefix. "Allowed" means anything but 401 and 403: a request that passes the chain may still be a
 * 400 or 404 from the controller, which is not this test's business.
 */
class StaffAccessIT extends CatalogTestSupport {

    /** One readable path per area, and the roles that may use it (auth/StaffArea.java). */
    private static final Map<String, List<String>> AREAS = Map.of(
            "/staff/overview", List.of("SUPPORT", "SALES", "ADMIN"),
            "/staff/orders", List.of("SUPPORT", "ADMIN"),
            "/staff/reviews?state=todo", List.of("SUPPORT", "ADMIN"),
            "/staff/products", List.of("SALES", "ADMIN"),
            "/staff/categories", List.of("SALES", "ADMIN"),
            "/staff/delivery", List.of("SALES", "ADMIN"),
            "/staff/users", List.of("ADMIN"),
            "/staff/audit", List.of("ADMIN"));

    @Test
    void eachRoleReachesItsOwnAreasAndGets403EverywhereElse() throws Exception {
        for (String role : List.of("SUPPORT", "SALES", "ADMIN")) {
            String token = staff(role).token();
            for (Map.Entry<String, List<String>> area : AREAS.entrySet()) {
                int status = mockMvc.perform(auth(get(area.getKey()), token)).andReturn().getResponse().getStatus();
                if (area.getValue().contains(role)) {
                    assertThat(status).as(role + " on " + area.getKey()).isNotIn(401, 403);
                } else {
                    assertThat(status).as(role + " on " + area.getKey()).isEqualTo(403);
                }
            }
        }
    }

    @Test
    void aShopperIsRefusedEveryStaffAreaAndAnonymousIsAskedToSignIn() throws Exception {
        String customer = customerToken();
        for (String path : AREAS.keySet()) {
            mockMvc.perform(auth(get(path), customer)).andExpect(status().isForbidden());
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void aStaffPathWithNoRuleOfItsOwnIsClosed() throws Exception {
        mockMvc.perform(auth(get("/staff/something-new"), adminToken())).andExpect(status().isForbidden());
    }

    @Test
    void catalogWritesAreForSalesAndAdmin_notSupport() throws Exception {
        String body = """
                {"name":"Matrix %s","slug":"matrix-%s","parentId":null}""";
        mockMvc.perform(json(post("/categories"), staff("SUPPORT").token(), body.formatted(suffix(), suffix())))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(post("/categories"), staff("SALES").token(), body.formatted(suffix(), suffix())))
                .andExpect(status().isCreated());
    }

    @Test
    void staffSignedInWithAPasswordOnly_canReachOnlyTheTwoFactorSetup() throws Exception {
        String email = registerUser();
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email_hash = ?", emailHash(email));
        String passwordOnly = tokenFor(email);

        mockMvc.perform(auth(get("/staff/overview"), passwordOnly))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("two_factor_required"));
        mockMvc.perform(json(post("/products"), passwordOnly, "{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("two_factor_required"));
        mockMvc.perform(json(post("/auth/2fa/setup"), passwordOnly, """
                        {"password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void turningTwoFactorOnOpensTheAdminInTheSameSession_andRefreshesKeepIt() throws Exception {
        String email = registerUser();
        jdbc.update("UPDATE users SET role = 'SUPPORT' WHERE email_hash = ?", emailHash(email));
        MvcResult login = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}""".formatted(email, PASSWORD))).andReturn();
        String passwordOnly = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        Cookie refresh = login.getResponse().getCookie("refresh_token");

        MvcResult setup = mockMvc.perform(json(post("/auth/2fa/setup"), passwordOnly, """
                {"password":"%s"}""".formatted(PASSWORD))).andExpect(status().isOk()).andReturn();
        String secret = JsonPath.read(setup.getResponse().getContentAsString(), "$.secret");
        MvcResult enabled = mockMvc.perform(json(post("/auth/2fa/enable"), passwordOnly, """
                        {"code":"%s"}""".formatted(currentCode(secret))).cookie(refresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backupCodes.length()").value(8))
                .andReturn();
        String upgraded = JsonPath.read(enabled.getResponse().getContentAsString(), "$.accessToken");

        assertThat(statusOf("/staff/overview", upgraded)).isNotIn(401, 403);
        mockMvc.perform(auth(get("/staff/overview"), passwordOnly)).andExpect(status().isForbidden());

        // the session itself was marked: a refresh tomorrow still opens the admin
        MvcResult refreshed = mockMvc.perform(post("/auth/refresh").cookie(refresh)).andExpect(status().isOk()).andReturn();
        String next = JsonPath.read(refreshed.getResponse().getContentAsString(), "$.accessToken");
        assertThat(statusOf("/staff/overview", next)).isNotIn(401, 403);
    }

    private int statusOf(String path, String token) throws Exception {
        return mockMvc.perform(auth(get(path), token)).andReturn().getResponse().getStatus();
    }

    @Test
    void aStaffAccountCantTurnTwoFactorOff() throws Exception {
        mockMvc.perform(json(post("/auth/2fa/disable"), staff("SALES").token(), """
                        {"password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("staff_needs_two_factor"));
    }
}
