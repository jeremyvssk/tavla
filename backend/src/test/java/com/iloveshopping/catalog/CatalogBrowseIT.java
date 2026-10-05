// Integration tests for public catalog reads against the seeded demo catalog: tree, subtree browse, detail.
package com.iloveshopping.catalog;

import com.iloveshopping.support.CatalogTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogBrowseIT extends CatalogTestSupport {

    /** Sunrise's "Ace" folding set (szachowo product 21): the seed keys products by supplier id, not name. */
    private static final String SEEDED_FOLDING_SET = "szachowo:21";
    /** YMI's medium magnetic Go set: sold in one version only, so it has no variant group. */
    private static final String SEEDED_SINGLE_GO_SET = "ymi:3768924867";

    @Test
    void categoryTree_isNestedThreeLevelsDeep() throws Exception {
        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug == 'chess')].children[*].slug", hasItem("chess-sets")))
                .andExpect(jsonPath("$[?(@.slug == 'chess')].children[?(@.slug == 'chess-sets')].children[*].slug",
                        hasItem("folding-sets")));
    }

    @Test
    void browsingATopLevelCategory_includesProductsFromEveryDescendant() throws Exception {
        String hidden = hiddenProductIn("folding-sets");
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
                .andExpect(jsonPath("$.items[*].id", not(hasItem(hidden))))
                // No search term, so relevance has nothing to rank: the listing falls back to featured.
                .andExpect(jsonPath("$.sort").value("featured"));
    }

    @Test
    void productDetail_carriesBreadcrumbBrandAndBothUnitSystems() throws Exception {
        UUID id = seededId(SEEDED_FOLDING_SET);
        mockMvc.perform(get("/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breadcrumb[*].slug", contains("chess", "chess-sets", "folding-sets")))
                .andExpect(jsonPath("$.brand.slug").value("sunrise-chess-games"))
                .andExpect(jsonPath("$.measurements.weightKg").value(1.2))
                .andExpect(jsonPath("$.measurements.weightLbs").value(2.65))
                .andExpect(jsonPath("$.measurements.widthIn").value(16.54))
                .andExpect(jsonPath("$.attributes.king_height_mm").value(100))
                .andExpect(jsonPath("$.images[0].primary").value(true))
                .andExpect(jsonPath("$.reviewCount").isNumber());
    }

    @Test
    void productDetail_listsEveryColourOfItsGroup_includingItself() throws Exception {
        UUID id = seededId(SEEDED_FOLDING_SET);
        mockMvc.perform(get("/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[*].options.Colour", containsInAnyOrder("Brown", "Green", "Blue")))
                .andExpect(jsonPath("$.variants[*].id", hasItem(id.toString())))
                .andExpect(jsonPath("$.variants[*].imageUrl", everyItem(notNullValue())));
    }

    @Test
    void productDetail_withoutAVariantGroup_hasNoVariants() throws Exception {
        mockMvc.perform(get("/products/{id}", seededId(SEEDED_SINGLE_GO_SET)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants").isEmpty());
    }

    @Test
    void inactiveOrMissingProducts_are404_andAMalformedIdIs400() throws Exception {
        mockMvc.perform(get("/products/{id}", hiddenProductIn("folding-sets")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("product_not_found"));
        mockMvc.perform(get("/products/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/products/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_parameter"));
    }

    @Test
    void featured_listsBuyableProductsWithPhotosFirst() throws Exception {
        String body = mockMvc.perform(get("/products").param("category", "backgammon").param("size", "48"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sort").value("featured"))
                .andReturn().getResponse().getContentAsString();
        List<Boolean> inStock = JsonPath.read(body, "$.items[*].inStock");
        List<Object> images = JsonPath.read(body, "$.items[*].primaryImageUrl");
        // the seed's backgammon shelf has both sold-out and photo-less products, so the order is really tested
        assertThat(inStock).contains(false);
        assertThat(images).containsNull();

        // rank 0 = in stock with a photo, 1 = in stock without, 2 = sold out; never decreasing down the page
        List<Integer> ranks = new ArrayList<>();
        for (int i = 0; i < inStock.size(); i++) {
            ranks.add(!inStock.get(i) ? 2 : images.get(i) == null ? 1 : 0);
        }
        assertThat(ranks).isSorted();
    }

    @Test
    void photoFraming_reachesTheProductPageAndTheListings() throws Exception {
        UUID id = seededId(SEEDED_SINGLE_GO_SET);
        jdbc.update("UPDATE product_images SET framing = ?::jsonb WHERE product_id = ? AND is_primary",
                "{\"ratio\": 1.5, \"box\": [0.1, 0.2, 0.9, 1], \"bleed\": \"b\", \"lift\": 1.04, \"print\": false}", id);
        String name = jdbc.queryForObject("SELECT name FROM products WHERE id = ?", String.class, id);

        mockMvc.perform(get("/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images[0].framing.bleed").value("b"))
                .andExpect(jsonPath("$.images[0].framing.box[3]").value(1.0))
                .andExpect(jsonPath("$.images[0].framing.lift").value(1.04))
                .andExpect(jsonPath("$.images[0].framing.print").value(false));
        // the listing reads the primary photo through its own SQL, so it is checked separately
        mockMvc.perform(get("/products").param("q", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + id + "')].primaryImageFraming.bleed", contains("b")));
    }

    @Test
    void brands_areListedPublicly() throws Exception {
        mockMvc.perform(get("/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", hasItem("sunrise-chess-games")));
    }

    private UUID seededId(String supplierKey) {
        return jdbc.queryForObject("SELECT md5('seed-product:' || ?)::uuid", UUID.class, supplierKey);
    }

    /** The seed has no inactive products, so each test that needs one hides a fresh product of its own. */
    private String hiddenProductIn(String categorySlug) throws Exception {
        int category = jdbc.queryForObject("SELECT id FROM categories WHERE slug = ?", Integer.class, categorySlug);
        String id = createProduct(adminToken(), "Discontinued Set " + suffix(), "No longer sold.", "99.00", category, null);
        jdbc.update("UPDATE products SET active = false WHERE id = ?::uuid", id);
        return id;
    }
}
