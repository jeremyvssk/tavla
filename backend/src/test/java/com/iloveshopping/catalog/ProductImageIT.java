// Integration tests for image upload: stored under a generated name, served path returned, files removed on delete.
package com.iloveshopping.catalog;

import com.iloveshopping.support.CatalogTestSupport;
import com.iloveshopping.support.TestImages;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductImageIT extends CatalogTestSupport {

    @Test
    void upload_storesAReencodedFile_andDeletingTheProductRemovesIt() throws Exception {
        String admin = adminToken();
        int category = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'go'", Integer.class);
        String productId = createProduct(admin, "Image Target " + suffix(), "", "10.00", category, null);

        MockMultipartFile file = new MockMultipartFile("file", "holiday photo.jpg", "image/jpeg", TestImages.jpeg(40, 30));
        String body = mockMvc.perform(auth(multipart("/products/{id}/images", productId).file(file)
                        .param("altText", "Board from above"), admin))
                .andExpect(status().isCreated())
                // The client's filename never survives: a UUID and the extension of the detected type.
                .andExpect(jsonPath("$.url", matchesPattern("^/images/products/[0-9a-f-]{36}\\.jpg$")))
                // First image of a product becomes its primary image automatically.
                .andExpect(jsonPath("$.primary").value(true))
                .andReturn().getResponse().getContentAsString();

        String url = JsonPath.read(body, "$.url");
        Path stored = imagesDir.resolve(url.substring("/images/".length()));
        assertThat(stored).exists();

        mockMvc.perform(get("/products/{id}", productId))
                .andExpect(jsonPath("$.images[0].url").value(url))
                .andExpect(jsonPath("$.images[0].altText").value("Board from above"));
        mockMvc.perform(get("/products").param("q", "Image Target"))
                .andExpect(jsonPath("$.items[0].primaryImageUrl").value(url));

        mockMvc.perform(auth(delete("/products/{id}", productId), admin))
                .andExpect(status().isNoContent());
        assertThat(Files.exists(stored)).isFalse();
    }

    @Test
    void upload_requiresAdmin() throws Exception {
        String admin = adminToken();
        int category = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'go'", Integer.class);
        String productId = createProduct(admin, "Image Auth " + suffix(), "", "10.00", category, null);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", TestImages.png(4, 4));

        mockMvc.perform(multipart("/products/{id}/images", productId).file(file))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(auth(multipart("/products/{id}/images", productId).file(file), customerToken()))
                .andExpect(status().isForbidden());
    }
}
