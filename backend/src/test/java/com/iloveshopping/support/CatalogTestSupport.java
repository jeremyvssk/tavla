// IT helpers for catalog tests: customer and admin tokens, and creating isolated catalog data over the API.
package com.iloveshopping.support;

import com.jayway.jsonpath.JsonPath;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The seeded demo catalog is present in every IT database. Tests that assert exact counts create
 * their own category and brands with a random suffix and filter on them, so they never depend on
 * seed contents or on each other.
 */
public abstract class CatalogTestSupport extends AbstractIntegrationTest {

    protected static final String PASSWORD = "password123";

    @Autowired
    protected JdbcTemplate jdbc;

    protected String customerToken() throws Exception {
        return tokenFor(registerUser());
    }

    /**
     * A shopper who has received the product: a delivered order holding it, written straight to the tables
     * (the order flow itself is OrderTestSupport's), since only a delivered purchase may be reviewed.
     */
    protected String buyerOf(String productId) throws Exception {
        String email = registerUser();
        UUID order = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO orders (id, number, user_id, status, email, phone, first_name, last_name, street, postcode, city, country,
                                    shipping_method, shipping_method_name, shipping_days_min, shipping_days_max,
                                    subtotal, shipping_cost, vat, total)
                SELECT ?, ?, id, 'DELIVERED', 'x', 'x', 'x', 'x', 'x', '10111', 'x', 'EE', 'standard', 'Standard courier', 2, 4,
                       10, 0, 1.94, 10 FROM users WHERE email_hash = ?""", order, "T" + suffix(), emailHash(email));
        jdbc.update("""
                INSERT INTO order_items (order_id, product_id, position, product_name, unit_price, quantity, line_total)
                VALUES (?, ?::uuid, 0, 'x', 10, 1, 10)""", order, productId);
        return tokenFor(email);
    }

    protected String adminToken() throws Exception {
        return staff("ADMIN").token();
    }

    /** A signed-in staff member, with what a test needs to approve a change as them: the 2FA secret. */
    protected record Staff(String token, String secret, UUID id, String email) {
    }

    /**
     * A staff account the way the app requires one: 2FA on, signed in with password and code, so the token
     * carries the second factor. There is deliberately no API for granting admin; in the running app it's a
     * psql UPDATE too, and these roles are set the same way here.
     */
    protected Staff staff(String role) throws Exception {
        String email = registerUser();
        String secret = new DefaultSecretGenerator().generate();
        jdbc.update("UPDATE users SET role = ?, two_factor_enabled = true, two_factor_secret = ? WHERE email_hash = ?",
                role, sealed(secret), emailHash(email));
        UUID id = jdbc.queryForObject("SELECT id FROM users WHERE email_hash = ?", UUID.class, emailHash(email));
        return new Staff(twoFactorTokenFor(email, secret), secret, id, email);
    }

    /** Password, then the current code from the secret: a session that passed the second factor. */
    protected String twoFactorTokenFor(String email, String secret) throws Exception {
        MvcResult login = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String challenge = JsonPath.read(login.getResponse().getContentAsString(), "$.challenge");
        MvcResult second = mockMvc.perform(post("/auth/2fa/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challenge":"%s","code":"%s"}""".formatted(challenge, currentCode(secret))))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(second.getResponse().getContentAsString(), "$.accessToken");
    }

    /** What the authenticator app shows right now for this secret. */
    protected static String currentCode(String secret) throws Exception {
        return new DefaultCodeGenerator().generate(secret, Math.floorDiv(Instant.now().getEpochSecond(), 30));
    }

    protected String registerUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","fullName":"Catalog Tester"}""".formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        return email;
    }

    protected String tokenFor(String email) throws Exception {
        MvcResult login = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
    }

    protected static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected int createCategory(String admin, String name, String slug, Integer parentId) throws Exception {
        String body = """
                {"name":"%s","slug":"%s","parentId":%s}""".formatted(name, slug, parentId);
        MvcResult result = mockMvc.perform(json(post("/categories"), admin, body))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    protected int createBrand(String admin, String name, String slug) throws Exception {
        String body = """
                {"name":"%s","slug":"%s"}""".formatted(name, slug);
        MvcResult result = mockMvc.perform(json(post("/brands"), admin, body))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    protected String createProduct(String admin, String name, String description, String price,
                                   int categoryId, Integer brandId) throws Exception {
        String body = """
                {"name":"%s","description":"%s","price":%s,"stockQuantity":5,"categoryId":%d,"brandId":%s}"""
                .formatted(name, description, price, categoryId, brandId);
        MvcResult result = mockMvc.perform(json(post("/products"), admin, body))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String token, String body) {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected static <B extends AbstractMockHttpServletRequestBuilder<B>> B auth(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
