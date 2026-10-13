// The staff's product list and bulk changes: every state, SKU search, the price limit for Sales, archive or delete, the export.
package com.iloveshopping.catalog;

import com.iloveshopping.support.CatalogTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StaffCatalogIT extends CatalogTestSupport {

    private String admin;
    private int shelf;
    private String tag;

    /** A shelf of its own per test, so the list's filters see only this test's products. */
    @BeforeEach
    void shelf() throws Exception {
        admin = adminToken();
        tag = suffix();
        shelf = createCategory(admin, "Staff list " + tag, "staff-list-" + tag, null);
    }

    @Test
    void theListShowsHiddenAndArchivedProducts_andCountsEachStateUnderTheSameFilters() throws Exception {
        String active = product("Active " + tag, "SL-A-" + tag, "20.00", 9);
        String hidden = product("Hidden " + tag, "SL-H-" + tag, "20.00", 3);
        String empty = product("Empty " + tag, "SL-E-" + tag, "20.00", 0);
        bulk(admin, "{\"ids\":[\"%s\"],\"action\":\"hide\"}".formatted(hidden)).andExpect(status().isOk());
        String archived = product("Archived " + tag, "SL-R-" + tag, "20.00", 1);
        ordered(archived);
        mockMvc.perform(auth(delete("/products/{id}", archived), admin)).andExpect(status().isNoContent());

        mockMvc.perform(auth(get("/staff/products"), admin).param("category", "staff-list-" + tag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.counts.all").value(3))
                .andExpect(jsonPath("$.counts.active").value(2))
                .andExpect(jsonPath("$.counts.hidden").value(1))
                .andExpect(jsonPath("$.counts.out").value(1))
                .andExpect(jsonPath("$.counts.low").value(1))
                .andExpect(jsonPath("$.counts.archived").value(1))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].state".formatted(hidden)).value("HIDDEN"))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].stockQuantity".formatted(empty)).value(0))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].sku".formatted(active)).value("SL-A-" + tag));
        mockMvc.perform(auth(get("/staff/products"), admin).param("category", "staff-list-" + tag).param("state", "archived"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].state").value("ARCHIVED"));
        // and the shop still knows only the active ones
        mockMvc.perform(get("/products/{id}", hidden)).andExpect(status().isNotFound());
        mockMvc.perform(get("/products/{id}", archived)).andExpect(status().isNotFound());
        mockMvc.perform(auth(get("/staff/products/{id}", archived), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void searchMatchesPartOfANameOrTheStartOfASku_andTypedWildcardsAreLiteral() throws Exception {
        product("Walnut Tournament Board " + tag, "WTB-" + tag, "80.00", 2);

        assertThat(names(auth(get("/staff/products"), admin).param("q", "tournament board " + tag))).hasSize(1);
        assertThat(names(auth(get("/staff/products"), admin).param("q", "wtb-" + tag))).hasSize(1);
        assertThat(names(auth(get("/staff/products"), admin).param("q", tag.substring(2)).param("category", "staff-list-" + tag)))
                .hasSize(1);
        assertThat(names(auth(get("/staff/products"), admin).param("q", "%").param("category", "staff-list-" + tag))).isEmpty();
    }

    @Test
    void aCategoryFilterTakesTheShelvesBelowIt() throws Exception {
        int below = createCategory(admin, "Below " + tag, "below-" + tag, shelf);
        createProduct(admin, "On the lower shelf " + tag, "x", "10.00", below, null);

        assertThat(names(auth(get("/staff/products"), admin).param("category", "staff-list-" + tag))).hasSize(1);
        assertThat(names(auth(get("/staff/products"), admin).param("category", "no-such-shelf"))).isEmpty();
    }

    @Test
    void aBulkPriceChangeIsAllOrNothing_andSalesStaysWithinHalf() throws Exception {
        String a = product("Price A " + tag, null, "10.00", 1);
        String b = product("Price B " + tag, null, "100.00", 1);
        Staff sales = staff("SALES");
        String ids = "[\"%s\",\"%s\"]".formatted(a, b);

        bulk(sales.token(), "{\"ids\":%s,\"action\":\"price\",\"mode\":\"raise\",\"value\":10}".formatted(ids))
                .andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(2));
        assertThat(price(a)).isEqualTo("11.00");
        assertThat(price(b)).isEqualTo("110.00");

        // setting both to 20 is +82% for one of them: refused, and neither changes
        bulk(sales.token(), "{\"ids\":%s,\"action\":\"price\",\"mode\":\"set\",\"value\":20}".formatted(ids))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("price_change_too_large"));
        assertThat(price(a)).isEqualTo("11.00");
        assertThat(price(b)).isEqualTo("110.00");

        bulk(admin, "{\"ids\":%s,\"action\":\"price\",\"mode\":\"set\",\"value\":20}".formatted(ids))
                .andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(2));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_log WHERE target_id IN (?, ?) AND action = 'Changed the price of {target}'""",
                Integer.class, a, b)).isEqualTo(4);
    }

    @Test
    void salesCantMoveOneProductsPriceByMoreThanHalfEither() throws Exception {
        String id = product("Single " + tag, null, "14.99", 1);
        String body = """
                {"name":"Single %s","price":%s,"stockQuantity":1,"categoryId":%d}""";

        mockMvc.perform(json(put("/products/{id}", id), staff("SALES").token(), body.formatted(tag, "1499.00", shelf)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("price_change_too_large"));
        mockMvc.perform(json(put("/products/{id}", id), admin, body.formatted(tag, "1499.00", shelf)))
                .andExpect(status().isOk());
    }

    @Test
    void aBulkChangeChecksItsOwnFields() throws Exception {
        String id = product("Fields " + tag, null, "10.00", 1);
        bulk(admin, "{\"ids\":[\"%s\"],\"action\":\"move\"}".formatted(id))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.categoryId").exists());
        bulk(admin, "{\"ids\":[\"%s\"],\"action\":\"price\",\"mode\":\"lower\",\"value\":100}".formatted(id))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.value").exists());
        bulk(admin, "{\"ids\":[],\"action\":\"hide\"}").andExpect(status().isBadRequest());
        bulk(admin, "{\"ids\":[\"%s\"],\"action\":\"delete\"}".formatted(id)).andExpect(status().isBadRequest());
        bulk(staff("SUPPORT").token(), "{\"ids\":[\"%s\"],\"action\":\"hide\"}".formatted(id)).andExpect(status().isForbidden());
    }

    @Test
    void moveAndShowAreCountedOnlyWhereSomethingChanged() throws Exception {
        int other = createCategory(admin, "Other " + tag, "other-" + tag, null);
        String a = product("Move A " + tag, null, "10.00", 1);
        String b = product("Move B " + tag, null, "10.00", 1);
        bulk(admin, "{\"ids\":[\"%s\"],\"action\":\"move\",\"categoryId\":%d}".formatted(a, other))
                .andExpect(jsonPath("$.changed").value(1));
        bulk(admin, "{\"ids\":[\"%s\",\"%s\"],\"action\":\"move\",\"categoryId\":%d}".formatted(a, b, other))
                .andExpect(jsonPath("$.changed").value(1));
        bulk(admin, "{\"ids\":[\"%s\"],\"action\":\"show\"}".formatted(a)).andExpect(jsonPath("$.changed").value(0));
    }

    @Test
    void deletingAnOrderedProductArchivesIt_andShowingItAgainBringsItBack() throws Exception {
        String neverSold = product("Never sold " + tag, null, "10.00", 1);
        String sold = product("Sold " + tag, null, "10.00", 1);
        ordered(sold);

        mockMvc.perform(auth(delete("/products/{id}", neverSold), admin)).andExpect(status().isNoContent());
        mockMvc.perform(auth(delete("/products/{id}", sold), admin)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE id = ?::uuid", Integer.class, neverSold)).isZero();
        assertThat(jdbc.queryForObject("SELECT archived_at IS NOT NULL AND NOT active FROM products WHERE id = ?::uuid",
                Boolean.class, sold)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_items WHERE product_id = ?::uuid", Integer.class, sold)).isEqualTo(1);

        bulk(admin, "{\"ids\":[\"%s\"],\"action\":\"show\"}".formatted(sold)).andExpect(jsonPath("$.changed").value(1));
        mockMvc.perform(get("/products/{id}", sold)).andExpect(status().isOk());
    }

    @Test
    void aSkuIsUniqueIgnoringCase() throws Exception {
        product("First " + tag, "DUP-" + tag, "10.00", 1);
        mockMvc.perform(json(post("/products"), admin, """
                        {"name":"Second","price":10,"stockQuantity":1,"categoryId":%d,"sku":"dup-%s"}""".formatted(shelf, tag.toUpperCase())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("sku_already_exists"));
    }

    @Test
    void theExportIsTheUploadsOwnColumns_withFormulasDefused() throws Exception {
        product("=HYPERLINK(\\\"x\\\") " + tag, "EXP-" + tag, "12.50", 4);

        String csv = mockMvc.perform(auth(get("/staff/products/export"), admin).param("category", "staff-list-" + tag))
                .andExpect(status().isOk())
                .andExpect(content().string(startsWith("﻿sku,name,description,price,stock,category,brand,ean,")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("EXP-" + tag + ",\"'=HYPERLINK(\"\"x\"\") " + tag + "\"").contains(",12.50,4,staff-list-" + tag + ",");
    }

    private String product(String name, String sku, String price, int stock) throws Exception {
        String body = """
                {"name":"%s","price":%s,"stockQuantity":%d,"categoryId":%d,"sku":%s}"""
                .formatted(name, price, stock, shelf, sku == null ? "null" : "\"" + sku + "\"");
        return JsonPath.read(mockMvc.perform(json(post("/products"), admin, body)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** An order line pointing at the product, as a placed order would leave. */
    private void ordered(String productId) {
        UUID order = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO orders (id, number, guest_token_hash, status, email, phone, first_name, last_name, street, postcode,
                                    city, country, shipping_method, shipping_method_name, shipping_days_min, shipping_days_max,
                                    subtotal, shipping_cost, vat, total)
                VALUES (?, ?, 'test', 'DELIVERED', 'x', 'x', 'x', 'x', 'x', '10111', 'x', 'EE', 'standard', 'Standard courier', 2, 4,
                        10, 0, 1.8, 10)""",
                order, "T" + suffix());
        jdbc.update("""
                INSERT INTO order_items (order_id, product_id, position, product_name, unit_price, quantity, line_total)
                VALUES (?, ?::uuid, 0, 'x', 10, 1, 10)""", order, productId);
    }

    private org.springframework.test.web.servlet.ResultActions bulk(String token, String body) throws Exception {
        return mockMvc.perform(json(post("/staff/products/bulk"), token, body));
    }

    private String price(String id) {
        return jdbc.queryForObject("SELECT price::text FROM products WHERE id = ?::uuid", String.class, id);
    }

    private List<String> names(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return JsonPath.read(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.items[*].name");
    }
}
