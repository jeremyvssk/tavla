// Delivery options run by staff: the checkout's list, adding and editing options, the free-shipping limit, and orders priced by them.
package com.iloveshopping.delivery;

import com.iloveshopping.support.OrderTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The options and the limit are one shop-wide table that every order test prices with, so each test here
 * puts back what it changed (the seeded three stay as the migration made them).
 */
class DeliveryIT extends OrderTestSupport {

    private static final String OPTION = """
            {"name":"%s","price":%s,"orderBy":"14:00","daysMin":1,"daysMax":2,"countries":%s,"active":%s,"freeOverLimit":false}""";

    @AfterEach
    void putTheShopBack() {
        jdbc.update("UPDATE shop_settings SET free_shipping_from = 100.00, free_shipping_on = TRUE");
        jdbc.update("UPDATE delivery_options SET active = FALSE WHERE id NOT IN ('standard', 'express', 'locker')");
        jdbc.update("UPDATE delivery_options SET position = CASE id WHEN 'standard' THEN 0 WHEN 'express' THEN 1 WHEN 'locker' THEN 2 ELSE 9 END");
    }

    @Test
    void theCheckoutListsTheActiveOptionsInOrder_withTheFreeShippingLimit() throws Exception {
        mockMvc.perform(get("/delivery-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freeShipping.from").value(100.00))
                .andExpect(jsonPath("$.freeShipping.on").value(true))
                .andExpect(jsonPath("$.options[0].id").value("standard"))
                .andExpect(jsonPath("$.options[0].orderBy").value("15:00"))
                .andExpect(jsonPath("$.options[0].countries.length()").value(4))
                .andExpect(jsonPath("$.options[*].active").value(not(hasItem(false))));
    }

    @Test
    void anOptionStaffAddIsChargedAtCheckout_andOneSwitchedOffOrElsewhereIsRefused() throws Exception {
        Staff sales = staff("SALES");
        String name = "Pickup " + suffix();
        MvcResult created = mockMvc.perform(json(post("/staff/delivery/options"), sales.token(),
                        OPTION.formatted(name, "2.50", "[\"EE\"]", true)))
                .andExpect(status().isCreated()).andReturn();
        String id = ((List<String>) JsonPath.read(created.getResponse().getContentAsString(),
                "$.options[?(@.name == '%s')].id".formatted(name))).getFirst();
        assertThat(id).startsWith("pickup-");

        String cookie = guestAdd(null, product("30.00", 5), 1);
        MvcResult placed = place(cookie, null, checkout(id));
        assertThat(placed.getResponse().getStatus()).isEqualTo(201);
        assertThat(JsonPath.<Double>read(placed.getResponse().getContentAsString(), "$.total")).isEqualTo(32.50);
        String order = JsonPath.read(placed.getResponse().getContentAsString(), "$.id");
        assertThat(jdbc.queryForObject("SELECT shipping_method_name FROM orders WHERE id = ?::uuid", String.class, order)).isEqualTo(name);

        // renaming it later doesn't rewrite what the order promised
        mockMvc.perform(json(put("/staff/delivery/options/{id}", id), sales.token(), OPTION.formatted("Renamed", "2.50", "[\"EE\"]", false)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT shipping_method_name FROM orders WHERE id = ?::uuid", String.class, order)).isEqualTo(name);
        mockMvc.perform(get("/delivery-options")).andExpect(jsonPath("$.options[*].id").value(not(hasItem(id))));

        String next = guestAdd(null, product("30.00", 5), 1);
        assertRefused(next, id);
        assertRefused(next, "drone");
        String latvia = checkout("standard").replace("\"10141\"", "\"LV-1050\"").replace("\"Tallinn\"", "\"Rīga\"")
                .replace("\"EE\"", "\"LV\"").replace("\"standard\"", "\"" + id + "\"");
        assertThat(place(next, null, latvia).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void theFreeShippingLimitIsTheOneTheOrderIsPricedWith() throws Exception {
        String admin = adminToken();
        mockMvc.perform(json(put("/staff/delivery/free-shipping"), admin, "{\"from\":25,\"on\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.freeShipping.from").value(25));
        MvcResult placed = place(guestAdd(null, product("30.00", 5), 1), null, checkout("standard"));
        assertThat(JsonPath.<Double>read(placed.getResponse().getContentAsString(), "$.total")).isEqualTo(30.00);

        mockMvc.perform(json(put("/staff/delivery/free-shipping"), admin, "{\"from\":25,\"on\":false}")).andExpect(status().isOk());
        placed = place(guestAdd(null, product("30.00", 5), 1), null, checkout("standard"));
        assertThat(JsonPath.<Double>read(placed.getResponse().getContentAsString(), "$.total")).isEqualTo(34.90);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_type = 'DELIVERY' AND change_to = 'Off'",
                Integer.class)).isPositive();
    }

    @Test
    void reorderingNamesEveryOptionOnce() throws Exception {
        String admin = adminToken();
        List<String> ids = JsonPath.read(mockMvc.perform(auth(get("/staff/delivery"), admin)).andReturn().getResponse().getContentAsString(),
                "$.options[*].id");
        List<String> reversed = ids.reversed();
        mockMvc.perform(json(put("/staff/delivery/order"), admin, "{\"ids\":%s}".formatted(json(reversed))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.options[0].id").value(reversed.getFirst()));
        mockMvc.perform(json(put("/staff/delivery/order"), admin, "{\"ids\":[\"standard\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.ids").exists());
    }

    @Test
    void anOptionsFieldsAreChecked_andOnlySalesAndAdminsEditThem() throws Exception {
        String admin = adminToken();
        mockMvc.perform(json(post("/staff/delivery/options"), admin, """
                        {"name":"Odd","price":-1,"orderBy":"25:00","daysMin":3,"daysMax":1,"countries":["SE"],"active":true,"freeOverLimit":false}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.price").exists())
                .andExpect(jsonPath("$.fields.orderBy").exists());
        mockMvc.perform(json(post("/staff/delivery/options"), admin, OPTION.formatted("Backwards", "1", "[\"EE\"]", true)
                        .replace("\"daysMin\":1,\"daysMax\":2", "\"daysMin\":3,\"daysMax\":1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.daysMax").exists());
        mockMvc.perform(json(put("/staff/delivery/options/{id}", "no-such"), admin, OPTION.formatted("X", "1", "[\"EE\"]", true)))
                .andExpect(status().isNotFound());
        mockMvc.perform(auth(get("/staff/delivery"), staff("SUPPORT").token())).andExpect(status().isForbidden());
    }

    private void assertRefused(String cookie, String option) throws Exception {
        MvcResult refused = place(cookie, null, checkout(option));
        assertThat(refused.getResponse().getStatus()).isEqualTo(400);
        assertThat(JsonPath.<String>read(refused.getResponse().getContentAsString(), "$.fields.shippingMethod")).isEqualTo("Choose a shipping option.");
    }

    private static String json(List<String> ids) {
        return ids.stream().map(id -> "\"" + id + "\"").reduce((a, b) -> a + "," + b).map(s -> "[" + s + "]").orElse("[]");
    }
}
