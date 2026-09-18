// Integration tests for search: relevance weighting, typo fallback, facet semantics, price bounds, sorting, paging, suggestions.
package com.iloveshopping.catalog;

import com.iloveshopping.support.CatalogTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Each test builds its own tiny catalog under a fresh category, with a nonsense word ("quxbar"
 * plus a random suffix) that appears nowhere else, so counts are exact regardless of the seed.
 */
class SearchIT extends CatalogTestSupport {

    private String admin;
    private String token;
    private String categorySlug;
    private String brandA;
    private String brandB;

    @BeforeEach
    void buildCatalog() throws Exception {
        admin = adminToken();
        String s = suffix();
        token = "quxbar" + s.replaceAll("[^a-z]", "");
        categorySlug = "search-it-" + s;
        int category = createCategory(admin, "Search IT " + s, categorySlug, null);
        brandA = "brand-a-" + s;
        brandB = "brand-b-" + s;
        int a = createBrand(admin, "Brand A " + s, brandA);
        int b = createBrand(admin, "Brand B " + s, brandB);

        // One product has the word in its name, one only in its description: the name must rank first.
        createProduct(admin, token + " Board", "A plain board.", "80.00", category, a);
        createProduct(admin, "Plain Pieces", "Pairs well with any " + token + " you own.", "150.00", category, a);
        createProduct(admin, "Other Clock", "Nothing to see here.", "200.00", category, b);
    }

    @Test
    void relevance_ranksANameMatchAboveADescriptionMatch() throws Exception {
        mockMvc.perform(get("/products").param("q", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.sort").value("relevance"))
                .andExpect(jsonPath("$.approximate").value(false))
                .andExpect(jsonPath("$.items[0].name").value(token + " Board"))
                .andExpect(jsonPath("$.items[1].name").value("Plain Pieces"));
    }

    @Test
    void noWholeWordMatch_fallsBackToNearMatchesOnTheName_withFacetsFromTheSameRows() throws Exception {
        // An unfinished word: full-text search needs the whole lexeme, so this matches nothing there.
        String fragment = token.substring(0, 5);
        mockMvc.perform(get("/products").param("q", fragment).param("category", categorySlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approximate").value(true))
                // Name only: "Plain Pieces" mentions the word in its description and is not a near match.
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].name").value(token + " Board"))
                .andExpect(jsonPath("$.facets.brands[?(@.slug == '" + brandA + "')].count", contains(1)));

        // Nothing close either: still an empty result, not the whole category.
        mockMvc.perform(get("/products").param("q", "zzzzqqqq").param("category", categorySlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void brandFacet_ignoresItsOwnFilter_soOtherBrandsStayVisible() throws Exception {
        mockMvc.perform(get("/products").param("category", categorySlug).param("brand", brandA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                // Brand A is ticked, but Brand B still shows its real count rather than 0.
                .andExpect(jsonPath("$.facets.brands[?(@.slug == '" + brandA + "')].count", contains(2)))
                .andExpect(jsonPath("$.facets.brands[?(@.slug == '" + brandB + "')].count", contains(1)))
                // Price counts do obey the brand filter: only Brand A's 80 and 150 products.
                .andExpect(jsonPath("$.facets.prices[0].count").value(0))
                .andExpect(jsonPath("$.facets.prices[1].count").value(2))
                .andExpect(jsonPath("$.facets.prices[2].count").value(0));
    }

    @Test
    void priceFilter_minIsInclusive_maxIsExclusive_likeTheFacetBands() throws Exception {
        mockMvc.perform(get("/products").param("category", categorySlug)
                        .param("minPrice", "80").param("maxPrice", "200").param("sort", "price_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].price").value(80.00))
                .andExpect(jsonPath("$.items[1].price").value(150.00));
    }

    @Test
    void sortingAndPaging_workTogether() throws Exception {
        mockMvc.perform(get("/products").param("category", categorySlug)
                        .param("sort", "price_desc").param("size", "1").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].price").value(150.00));
    }

    @Test
    void suggestions_matchAFragment_butNotWildcardsOrSingleCharacters() throws Exception {
        mockMvc.perform(get("/search/suggestions").param("q", token.substring(0, 5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem(token + " Board")));
        // Escaped: "%" is a literal percent sign, not "match everything".
        mockMvc.perform(get("/search/suggestions").param("q", "%%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
        mockMvc.perform(get("/search/suggestions").param("q", "w"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void seededCatalog_findsAttributeValues_throughTheWeightedVector() throws Exception {
        // The Olympiad set mentions sheesham only inside attributes.wood, not in its name or description.
        mockMvc.perform(get("/products").param("q", "sheesham"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name", hasItem("Olympiad Weighted Staunton Set")));
    }
}
