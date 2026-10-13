// The admin's users page: finding accounts, and the rules around changing a role.
package com.iloveshopping.user;

import com.iloveshopping.support.CatalogTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StaffUserIT extends CatalogTestSupport {

    @Test
    void anAdminFindsAnAccountByItsWholeEmail_inAnyCase() throws Exception {
        Staff admin = staff("ADMIN");
        String email = registerUser();

        mockMvc.perform(auth(get("/staff/users"), admin.token())
                        .param("group", "all").param("q", email.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].email").value(email))
                .andExpect(jsonPath("$.items[0].role").value("CUSTOMER"))
                .andExpect(jsonPath("$.items[0].orderCount").value(0))
                .andExpect(jsonPath("$.counts.admins").isNumber());
        // The email is encrypted, so only the whole address matches (its blind index), never the start of one.
        mockMvc.perform(auth(get("/staff/users"), admin.token()).param("group", "all").param("q", email.substring(0, 20)))
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(auth(get("/staff/users"), admin.token()).param("group", "all").param("q", "%"))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void theStaffTabListsOnlyStaff_andTwoFactorOffNarrowsIt() throws Exception {
        Staff admin = staff("ADMIN");
        String noTwoFactor = registerUser();
        jdbc.update("UPDATE users SET role = 'SALES' WHERE email_hash = ?", emailHash(noTwoFactor));

        MvcResult staffTab = mockMvc.perform(auth(get("/staff/users"), admin.token()).param("size", "100"))
                .andExpect(status().isOk()).andReturn();
        List<String> roles = JsonPath.read(staffTab.getResponse().getContentAsString(), "$.items[*].role");
        assertThat(roles).isNotEmpty().doesNotContain("CUSTOMER");

        MvcResult off = mockMvc.perform(auth(get("/staff/users"), admin.token()).param("twoFactor", "off").param("size", "100"))
                .andExpect(status().isOk()).andReturn();
        List<Boolean> twoFactor = JsonPath.read(off.getResponse().getContentAsString(), "$.items[*].twoFactorEnabled");
        List<String> emails = JsonPath.read(off.getResponse().getContentAsString(), "$.items[*].email");
        assertThat(twoFactor).doesNotContain(true);
        assertThat(emails).contains(noTwoFactor);
    }

    @Test
    void aRoleChangeNeedsTheChangersOwnCode() throws Exception {
        Staff admin = staff("ADMIN");
        UUID target = idOf(registerUser());

        mockMvc.perform(json(put("/staff/users/" + target + "/role"), admin.token(), """
                        {"role":"SUPPORT","code":"000000"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_2fa_code"));
        mockMvc.perform(json(put("/staff/users/" + target + "/role"), admin.token(), """
                        {"role":"SUPPORT","code":"12ab"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.code").exists());
        assertThat(roleOf(target)).isEqualTo("CUSTOMER");
    }

    @Test
    void aPromotionTakesEffectAtOnce_andIsAudited() throws Exception {
        Staff admin = staff("ADMIN");
        Staff target = staff("SALES");
        assertAllowed("/staff/products", target.token());

        mockMvc.perform(json(put("/staff/users/" + target.id() + "/role"), admin.token(), """
                        {"role":"SUPPORT","code":"%s"}""".formatted(currentCode(admin.secret()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPPORT"));

        // the token issued before the change still says SALES, and is no longer accepted at all
        mockMvc.perform(auth(get("/staff/products"), target.token())).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_log WHERE actor_id = ? AND target_id = ? AND change_from = 'Sales' AND change_to = 'Support'""",
                Integer.class, admin.id(), target.id().toString())).isEqualTo(1);
        Thread.sleep(1_100); // a token from the cut-off's own second is refused too; sign in a second later
        String fresh = twoFactorTokenFor(target.email(), target.secret());
        mockMvc.perform(auth(get("/staff/products"), fresh)).andExpect(status().isForbidden());
        assertAllowed("/staff/orders", fresh);
    }

    @Test
    void nobodyChangesTheirOwnRole_andAdminIsNeverGrantedOrTakenHere() throws Exception {
        Staff admin = staff("ADMIN");
        Staff otherAdmin = staff("ADMIN");
        UUID customer = idOf(registerUser());

        assertConflict(admin, admin.id(), "SUPPORT", "own_role");
        assertConflict(admin, otherAdmin.id(), "SUPPORT", "admin_role_locked");
        assertConflict(admin, customer, "ADMIN", "admin_role_locked");
        assertThat(roleOf(otherAdmin.id())).isEqualTo("ADMIN");
        assertThat(roleOf(customer)).isEqualTo("CUSTOMER");
    }

    @Test
    void aGoogleAccountCantBeStaff() throws Exception {
        Staff admin = staff("ADMIN");
        UUID google = idOf(registerUser());
        jdbc.update("UPDATE users SET auth_provider = 'GOOGLE' WHERE id = ?", google);

        assertConflict(admin, google, "SALES", "staff_needs_password_account");
    }

    @Test
    void onlyAnAdminSeesTheUsers() throws Exception {
        mockMvc.perform(auth(get("/staff/users"), staff("SUPPORT").token())).andExpect(status().isForbidden());
        UUID target = idOf(registerUser());
        Staff sales = staff("SALES");
        mockMvc.perform(json(put("/staff/users/" + target + "/role"), sales.token(), """
                        {"role":"SALES","code":"%s"}""".formatted(currentCode(sales.secret()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnknownGroupOrPageSizeIsABadRequest() throws Exception {
        String admin = adminToken();
        mockMvc.perform(auth(get("/staff/users"), admin).param("group", "admins")).andExpect(status().isBadRequest());
        mockMvc.perform(auth(get("/staff/users"), admin).param("size", "5000")).andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/staff/users/" + UUID.randomUUID() + "/role"), admin, "{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    /** Past the security chain: whatever the controller answers, it isn't 401 or 403. */
    private void assertAllowed(String path, String token) throws Exception {
        assertThat(mockMvc.perform(auth(get(path), token)).andReturn().getResponse().getStatus()).as(path).isNotIn(401, 403);
    }

    private void assertConflict(Staff admin, UUID target, String role, String code) throws Exception {
        mockMvc.perform(json(put("/staff/users/" + target + "/role"), admin.token(), """
                        {"role":"%s","code":"%s"}""".formatted(role, currentCode(admin.secret()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(code));
    }

    private UUID idOf(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email_hash = ?", UUID.class, emailHash(email));
    }

    private String roleOf(UUID id) {
        return jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, id);
    }
}
