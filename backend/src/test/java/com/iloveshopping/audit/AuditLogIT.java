// The audit log: staff sign-ins land in it, admins read and export it, and nobody can rewrite it.
package com.iloveshopping.audit;

import com.iloveshopping.support.CatalogTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditLogIT extends CatalogTestSupport {

    @Test
    void aStaffSignInIsLogged_withTheAddressCut() throws Exception {
        Staff support = staff("SUPPORT");
        Staff admin = staff("ADMIN");

        mockMvc.perform(auth(get("/staff/audit"), admin.token()).param("actor", support.id().toString()).param("kind", "access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("Signed in"))
                .andExpect(jsonPath("$.items[0].actor").value("Catalog Tester"))
                .andExpect(jsonPath("$.items[0].actorRole").value("SUPPORT"))
                .andExpect(jsonPath("$.items[0].detail").value("Password and two-factor code"))
                .andExpect(jsonPath("$.items[0].ip").value("127.0.•.•"))
                .andExpect(jsonPath("$.actors[?(@.id == '%s')]".formatted(support.id())).exists());
    }

    @Test
    void aWrongPasswordOnAStaffAccountIsLoggedAgainstTheAccount_notAShoppersOne() throws Exception {
        Staff sales = staff("SALES");
        String shopper = registerUser();
        for (String email : new String[] {sales.email(), shopper}) {
            mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"email":"%s","password":"not-it"}""".formatted(email))).andExpect(status().isUnauthorized());
        }
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_log WHERE target_id = ? AND flag_text = 'Wrong password' AND actor_id IS NULL""",
                Integer.class, sales.id().toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log a JOIN users u ON a.target_id = u.id::text WHERE u.email_hash = ?",
                Integer.class, emailHash(shopper))).isZero();
    }

    @Test
    void theLogCantBeEditedOrDeletedInsideItsTwelveMonths() throws Exception {
        staff("ADMIN");
        Long id = jdbc.queryForObject("SELECT max(id) FROM audit_log", Long.class);

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_log SET action = 'Nothing happened' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_log WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("12 months");
    }

    @Test
    void deletingAStaffAccountKeepsItsEntries_withoutAName() throws Exception {
        Staff gone = staff("SALES");
        jdbc.update("DELETE FROM users WHERE id = ?", gone.id());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'Signed in' AND actor_id IS NULL AND actor_role = 'SALES'",
                Integer.class)).isPositive();
    }

    @Test
    void theExportIsACsvFileWithAHeaderRow() throws Exception {
        String admin = adminToken();
        mockMvc.perform(auth(get("/staff/audit/export"), admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"tavla-audit-log.csv\""))
                .andExpect(content().string(startsWith("﻿When,Who,Role,What,Before,After,Detail,Flag,From\r\n")));
    }

    @Test
    void onlyAdminsReadTheLog_andTheFiltersTakeOnlyKnownValues() throws Exception {
        mockMvc.perform(auth(get("/staff/audit"), staff("SUPPORT").token())).andExpect(status().isForbidden());
        String admin = adminToken();
        mockMvc.perform(auth(get("/staff/audit"), admin).param("kind", "everything")).andExpect(status().isBadRequest());
        mockMvc.perform(auth(get("/staff/audit"), admin).param("since", "1y")).andExpect(status().isBadRequest());
        mockMvc.perform(auth(get("/staff/audit"), admin).param("actor", "not-a-uuid")).andExpect(status().isBadRequest());
    }
}
