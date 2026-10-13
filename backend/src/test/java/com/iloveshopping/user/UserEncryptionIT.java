// Integration tests for users' encryption at rest (V16): ciphertext in the table, sign-in by the blind index, the backfill.
package com.iloveshopping.user;

import com.iloveshopping.support.CatalogTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserEncryptionIT extends CatalogTestSupport {

    @Autowired
    private Flyway flyway;

    @Autowired
    private UserService userService;

    @Test
    void theTableHoldsCiphertext_andTheAccountWorksAsBefore() throws Exception {
        String email = registerUser();

        Map<String, Object> row = jdbc.queryForMap("SELECT email, full_name, email_hash FROM users WHERE email_hash = ?",
                emailHash(email));
        assertThat((String) row.get("email")).startsWith("v1:").doesNotContain("example.com");
        assertThat((String) row.get("full_name")).startsWith("v1:").doesNotContain("Catalog Tester");
        assertThat((String) row.get("email_hash")).matches("[0-9a-f]{64}");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email)).isZero();

        // Sign-in finds the row by the hash of the lower-cased address, so the case typed doesn't matter.
        String token = tokenFor(email.toUpperCase());
        mockMvc.perform(auth(get("/users/me"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Catalog Tester"));
    }

    @Test
    void aSecondAccountWithTheSameEmail_isStillRefused_inAnyCase() throws Exception {
        String email = registerUser();
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"Copy Cat"}""".formatted(email.toUpperCase())))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email_hash = ?", Integer.class, emailHash(email)))
                .isEqualTo(1);
    }

    @Test
    void theTwoFactorSecret_isStoredEncrypted() throws Exception {
        String email = registerUser();
        String token = tokenFor(email);
        String body = mockMvc.perform(auth(post("/auth/2fa/setup"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secret = JsonPath.read(body, "$.secret");

        String stored = jdbc.queryForObject("SELECT two_factor_secret FROM users WHERE email_hash = ?", String.class,
                emailHash(email));
        assertThat(stored).startsWith("v1:").doesNotContain(secret);
    }

    @Test
    void aRowLeftInPlainText_isEncryptedByTheNextMigrate() {
        // What a database from before V16 holds, and what the SQL seed inserts for its reviewers.
        UUID id = UUID.randomUUID();
        String email = "plain-" + id + "@example.com";
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, full_name, auth_provider, role)
                VALUES (?, ?, '!no-login', 'Plain Person', 'LOCAL', 'CUSTOMER')""", id, email);

        flyway.migrate();

        Map<String, Object> row = jdbc.queryForMap("SELECT email, full_name, email_hash FROM users WHERE id = ?", id);
        assertThat((String) row.get("email")).startsWith("v1:");
        assertThat((String) row.get("full_name")).startsWith("v1:");
        assertThat(row.get("email_hash")).isEqualTo(emailHash(email));
        User found = userService.findByEmail(email).orElseThrow();
        assertThat(found.getFullName()).isEqualTo("Plain Person");
    }

    @Test
    void theSeedsReviewers_areEncrypted_andTheirNamesStillShowOnReviews() throws Exception {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM users
                WHERE id::text LIKE 'a0000000-0000-4000-8000-%' AND (email NOT LIKE 'v1:%' OR full_name NOT LIKE 'v1:%')""",
                Integer.class)).isZero();

        UUID product = jdbc.queryForObject("""
                SELECT product_id FROM product_reviews WHERE user_id = 'a0000000-0000-4000-8000-000000000001' LIMIT 1""",
                UUID.class);
        String body = mockMvc.perform(get("/products/{id}/reviews", product).param("size", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("Mari K.");
    }
}
