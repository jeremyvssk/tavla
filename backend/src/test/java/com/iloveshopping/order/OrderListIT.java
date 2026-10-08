// Integration tests for the account's order list (filters, sort, paging, counts) and the checkout prefill.
package com.iloveshopping.order;

import com.iloveshopping.support.OrderTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Each test gets a fresh account with five orders, then sets their statuses, totals and dates directly
 * in the table, so the filters and sorts have something to tell apart.
 */
class OrderListIT extends OrderTestSupport {

    private String token;
    /** oldest first: placed on 2026-01-10, 02-10, 03-10, 04-10, 05-10 (Tallinn time, midday) */
    private final List<String> ids = new ArrayList<>();

    @BeforeEach
    void fiveOrders() throws Exception {
        token = customerToken();
        String[] statuses = {"DELIVERED", "CANCELLED", "PAYMENT_FAILED", "SHIPPED", "PAYMENT_SUCCESSFUL"};
        String[] totals = {"30.00", "500.00", "20.00", "120.00", "75.00"};
        for (int i = 0; i < 5; i++) {
            String clock = product("10.00", 5);
            userAdd(token, clock, 1);
            String id = placeOk(null, token);
            jdbc.update("UPDATE orders SET status = ?, subtotal = ?, shipping_cost = 0, total = ?, vat = 0, "
                            + "placed_at = timestamp '2026-0" + (i + 1) + "-10 10:00:00' WHERE id = ?",
                    statuses[i], new BigDecimal(totals[i]), new BigDecimal(totals[i]), UUID.fromString(id));
            ids.add(id);
        }
    }

    private List<String> listed(String query) throws Exception {
        String body = mockMvc.perform(auth(get("/orders" + query), token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.items[*].id");
    }

    @Test
    void newestFirst_withCountsPerStatusGroup() throws Exception {
        mockMvc.perform(auth(get("/orders"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.items[0].id").value(ids.get(4)))
                .andExpect(jsonPath("$.items[0].number").exists())
                .andExpect(jsonPath("$.items[0].itemCount").value(1))
                .andExpect(jsonPath("$.items[0].preview[0].name").value(startsWith("Order item")))
                .andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.counts.all").value(5))
                .andExpect(jsonPath("$.counts.in_progress").value(2))
                .andExpect(jsonPath("$.counts.delivered").value(1))
                .andExpect(jsonPath("$.counts.cancelled").value(1))
                .andExpect(jsonPath("$.counts.failed").value(1));
    }

    @Test
    void filtersByStatusGroup_andDateRange_together() throws Exception {
        assertThat(listed("?status=in_progress")).containsExactly(ids.get(4), ids.get(3));
        assertThat(listed("?status=failed")).containsExactly(ids.get(2));
        // both days inclusive
        assertThat(listed("?from=2026-02-10&to=2026-04-10")).containsExactly(ids.get(3), ids.get(2), ids.get(1));
        assertThat(listed("?status=in_progress&from=2026-02-01&to=2026-04-30")).containsExactly(ids.get(3));
        // the counts follow the date range, not the status filter, so the pills say what each would show
        mockMvc.perform(auth(get("/orders?status=failed&from=2026-04-01"), token))
                .andExpect(jsonPath("$.counts.all").value(2))
                .andExpect(jsonPath("$.counts.failed").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void sortsByDate_total_andStatus() throws Exception {
        assertThat(listed("?sort=oldest")).containsExactly(ids.get(0), ids.get(1), ids.get(2), ids.get(3), ids.get(4));
        assertThat(listed("?sort=total_desc")).containsExactly(ids.get(1), ids.get(3), ids.get(4), ids.get(0), ids.get(2));
        // what needs the shopper first: failed, then paid, shipped, delivered, cancelled
        assertThat(listed("?sort=status")).containsExactly(ids.get(2), ids.get(4), ids.get(3), ids.get(0), ids.get(1));
    }

    @Test
    void pagesThroughTheList() throws Exception {
        mockMvc.perform(auth(get("/orders?size=2&page=1"), token))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(ids.get(2)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(3));
        mockMvc.perform(auth(get("/orders?size=2&page=9"), token)).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void showsOnlyTheAccountsOwnOrders_andNeedsALogin() throws Exception {
        String other = customerToken();
        mockMvc.perform(auth(get("/orders"), other))
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.counts.all").value(0));
        mockMvc.perform(get("/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void badParameters_are400_neverSql() throws Exception {
        mockMvc.perform(auth(get("/orders?status=everything"), token)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.status").exists());
        mockMvc.perform(auth(get("/orders").param("sort", "placed_at; DROP TABLE orders"), token)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.sort").exists());
        mockMvc.perform(auth(get("/orders?size=500"), token)).andExpect(status().isBadRequest());
        mockMvc.perform(auth(get("/orders?from=yesterday"), token)).andExpect(status().isBadRequest());
        mockMvc.perform(auth(get("/orders?from=2026-05-01&to=2026-04-01"), token)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.to").value("The end date is before the start date."));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isPositive();
    }

    @Test
    void theLastDelivery_prefillsTheNextCheckout() throws Exception {
        String fresh = customerToken();
        mockMvc.perform(auth(get("/orders/last-delivery"), fresh)).andExpect(status().isNoContent());

        mockMvc.perform(auth(get("/orders/last-delivery"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+372 5876 2210"))
                .andExpect(jsonPath("$.street").value("Pärnu mnt 22"))
                .andExpect(jsonPath("$.postcode").value("10141"))
                .andExpect(jsonPath("$.city").value("Tallinn"))
                .andExpect(jsonPath("$.country").value("EE"));
        mockMvc.perform(get("/orders/last-delivery")).andExpect(status().isUnauthorized());
    }
}
