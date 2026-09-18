// IT helpers for catalog tests: customer and admin tokens, and creating isolated catalog data over the API.
package com.iloveshopping.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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

    protected String adminToken() throws Exception {
        String email = registerUser();
        // There is deliberately no API for granting admin; in the running app it's a psql UPDATE too.
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", email);
        return tokenFor(email);
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
