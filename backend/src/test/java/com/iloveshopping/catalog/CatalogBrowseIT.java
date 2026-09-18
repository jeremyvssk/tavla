// Integration tests for public catalog reads against the seeded demo catalog: tree, subtree browse, detail.
package com.iloveshopping.catalog;

import com.iloveshopping.support.CatalogTestSupport;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogBrowseIT extends CatalogTestSupport {

    private static final String SEEDED_LUXURY_SET = "Ebony and Boxwood Imperial Set";

    @Test
    void categoryTree_isNestedThreeLevelsDeep() throws Exception {
        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug == 'chess')].children[*].slug", hasItem("chess-sets")))
                .andExpect(jsonPath("$[?(@.slug == 'chess')].children[?(@.slug == 'chess-sets')].children[*].slug",
                        hasItem("luxury-sets")));
    }

    @Test
    void browsingATopLevelCategory_includesProductsFromEveryDescendant() throws Exception {
        long direct = jdbc.queryForObject(
                "SELECT count(*) FROM products p JOIN categories c ON c.id = p.category_id WHERE c.slug = 'chess' AND p.active",
                Long.class);
        long subtree = jdbc.queryForObject("""
                WITH RECURSIVE t AS (SELECT id FROM categories WHERE slug = 'chess'
                                     UNION ALL SELECT c.id FROM categories c JOIN t ON c.parent_id = t.id)
                SELECT count(*) FROM products WHERE active AND category_id IN (SELECT id FROM t)""", Long.class);
        assertThat(direct).isZero();
        assertThat(subtree).isPositive();

        mockMvc.perform(get("/products").param("category", "chess").param("size", "48"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(subtree))
                .andExpect(jsonPath("$.items[*].name", not(hasItem("Discontinued Brass Chess Set"))))
                // No search term, so relevance has nothing to rank: the listing falls back to newest.
                .andExpect(jsonPath("$.sort").value("newest"));
    }

    @Test
    void productDetail_carriesBreadcrumbBrandAndBothUnitSystems() throws Exception {
        UUID id = seededId(SEEDED_LUXURY_SET);
        mockMvc.perform(get("/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breadcrumb[*].slug", contains("chess", "chess-sets", "luxury-sets")))
                .andExpect(jsonPath("$.brand.slug").value("ebony-and-box"))
                .andExpect(jsonPath("$.measurements.weightKg").value(5.8))
                .andExpect(jsonPath("$.measurements.weightLbs").value(12.79))
                .andExpect(jsonPath("$.measurements.widthIn").value(23.62))
                .andExpect(jsonPath("$.attributes.king_height_mm").value(102))
                .andExpect(jsonPath("$.reviewCount").isNumber());
    }

    @Test
    void inactiveOrMissingProducts_are404_andAMalformedIdIs400() throws Exception {
        mockMvc.perform(get("/products/{id}", seededId("Discontinued Brass Chess Set")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("product_not_found"));
        mockMvc.perform(get("/products/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/products/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_parameter"));
    }

    @Test
    void brands_areListedPublicly() throws Exception {
        mockMvc.perform(get("/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", hasItem("rank-and-file")));
    }

    private UUID seededId(String name) {
        return jdbc.queryForObject("SELECT id FROM products WHERE name = ?", UUID.class, name);
    }
}
