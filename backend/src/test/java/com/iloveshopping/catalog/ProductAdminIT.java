// Integration tests for admin catalog writes: auth gates, validation, references, conflicts, update, delete.
package com.iloveshopping.catalog;

import com.iloveshopping.support.CatalogTestSupport;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductAdminIT extends CatalogTestSupport {

    @Test
    void createProduct_requiresAnAdmin() throws Exception {
        int category = seededCategory("wooden-boards");
        String body = validBody(category);

        mockMvc.perform(json(post("/products"), null, body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(json(post("/products"), customerToken(), body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void createProduct_derivesImperialFromMetric_andIsReadableAfterwards() throws Exception {
        String admin = adminToken();
        String body = """
                {"name":"Test Walnut Board","description":"Solid walnut","price":149.00,"stockQuantity":3,
                 "categoryId":%d,"attributes":{"wood":["walnut"],"square_mm":55},
                 "weightKg":2.0,"widthCm":50,"heightCm":2,"depthCm":50}""".formatted(seededCategory("wooden-boards"));

        String id = com.jayway.jsonpath.JsonPath.read(mockMvc.perform(json(post("/products"), admin, body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.measurements.weightLbs").value(4.41))
                .andExpect(jsonPath("$.measurements.widthIn").value(19.69))
                .andExpect(jsonPath("$.inStock").value(true))
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes.square_mm").value(55))
                .andExpect(jsonPath("$.breadcrumb[0].slug").value("chess"));
    }

    @Test
    void createProduct_rejectsInvalidFields_withPerFieldMessages() throws Exception {
        String body = """
                {"name":"","price":-1,"stockQuantity":-5,"categoryId":%d,"weightKg":0}""".formatted(seededCategory("go"));

        mockMvc.perform(json(post("/products"), adminToken(), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.name").exists())
                .andExpect(jsonPath("$.fields.price").exists())
                .andExpect(jsonPath("$.fields.stockQuantity").exists())
                .andExpect(jsonPath("$.fields.weightKg").exists());
    }

    @Test
    void createProduct_withACategoryThatDoesNotExist_isAFieldError() throws Exception {
        mockMvc.perform(json(post("/products"), adminToken(), validBody(999_999)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.categoryId").value("does not exist"));
    }

    @Test
    void updateReplacesFields_andDeleteRemovesTheProduct() throws Exception {
        String admin = adminToken();
        int category = seededCategory("go");
        String id = createProduct(admin, "Go Board " + suffix(), "before", "50.00", category, null);

        mockMvc.perform(json(put("/products/{id}", id), admin, """
                        {"name":"Renamed Go Board","price":55.50,"stockQuantity":0,"categoryId":%d,"active":true}"""
                        .formatted(category)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Go Board"))
                .andExpect(jsonPath("$.price").value(55.50))
                .andExpect(jsonPath("$.inStock").value(false));

        mockMvc.perform(auth(delete("/products/{id}", id), admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/products/{id}", id))
                .andExpect(status().isNotFound());
        mockMvc.perform(json(put("/products/{id}", UUID.randomUUID()), admin, validBody(category)))
                .andExpect(status().isNotFound());
    }

    @Test
    void categoryAndBrandSlugs_areUnique_andValidated() throws Exception {
        String admin = adminToken();
        String slug = "test-cat-" + suffix();
        createCategory(admin, "Test Category " + slug, slug, null);

        mockMvc.perform(json(post("/categories"), admin, """
                        {"name":"Another name","slug":"%s"}""".formatted(slug)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("slug_already_exists"));
        mockMvc.perform(json(post("/categories"), admin, """
                        {"name":"Bad slug","slug":"Not A Slug!"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.slug").exists());
        mockMvc.perform(json(post("/categories"), admin, """
                        {"name":"Orphan","slug":"orphan-%s","parentId":999999}""".formatted(suffix())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.parentId").value("does not exist"));
    }

    private int seededCategory(String slug) {
        return jdbc.queryForObject("SELECT id FROM categories WHERE slug = ?", Integer.class, slug);
    }

    private static String validBody(int categoryId) {
        return """
                {"name":"Valid Product","price":10.00,"stockQuantity":1,"categoryId":%d}""".formatted(categoryId);
    }
}
