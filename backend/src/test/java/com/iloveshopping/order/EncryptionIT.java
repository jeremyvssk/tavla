// Integration tests for encryption at rest: what the database holds for orders, their lines and payments, versus what the API shows.
package com.iloveshopping.order;

import com.iloveshopping.support.OrderTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The automated half of the brief's manual check: psql sees ciphertext, the shop sees the address. */
class EncryptionIT extends OrderTestSupport {

    private String paidGuestOrder(String cookie) throws Exception {
        String id = placeOk(cookie, null);
        paidWith("visa", "4242");
        deliver(succeeded(id));
        return id;
    }

    @Test
    void theDatabaseHoldsCiphertext_andTheApiShowsTheRealValues() throws Exception {
        String clock = product("65.00", 4);
        String cookie = guestAdd(null, clock, 1);
        String id = paidGuestOrder(cookie);
        UUID orderId = UUID.fromString(id);

        Map<String, Object> order = jdbc.queryForMap(
                "SELECT email, phone, first_name, last_name, street, postcode, city, country, total FROM orders WHERE id = ?", orderId);
        for (String column : List.of("email", "phone", "first_name", "last_name", "street", "postcode", "city")) {
            assertThat((String) order.get(column)).as(column).startsWith("v1:");
        }
        assertThat(order.values().toString()).doesNotContain("mart.kask").doesNotContain("Kask").doesNotContain("Pärnu")
                .doesNotContain("10141").doesNotContain("Tallinn");
        // what queries filter and sort on stays plain
        assertThat(order.get("country")).isEqualTo("EE");

        Map<String, Object> line = jdbc.queryForMap("SELECT product_name, unit_price FROM order_items WHERE order_id = ?", orderId);
        assertThat((String) line.get("product_name")).startsWith("v1:").doesNotContain("Order item");

        Map<String, Object> payment = jdbc.queryForMap(
                "SELECT provider_payment_id, card_brand, card_last4, amount, status FROM payments WHERE order_id = ?", orderId);
        for (String column : List.of("provider_payment_id", "card_brand", "card_last4")) {
            assertThat((String) payment.get(column)).as(column).startsWith("v1:");
        }
        assertThat(payment.values().toString()).doesNotContain("pi_").doesNotContain("visa").doesNotContain("4242");
        assertThat(payment.get("status")).isEqualTo("SUCCEEDED");

        mockMvc.perform(as(get("/orders/" + id), cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("mart.kask@example.com"))
                .andExpect(jsonPath("$.shipTo.street").value("Pärnu mnt 22"))
                .andExpect(jsonPath("$.shipTo.city").value("Tallinn"))
                .andExpect(jsonPath("$.lines[0].name").value(startsWith("Order item")))
                .andExpect(jsonPath("$.payment.brand").value("Visa"))
                .andExpect(jsonPath("$.payment.last4").value("4242"));
    }

    @Test
    void theSameAddressTwice_isStoredDifferentlyEachTime() throws Exception {
        String clock = product("65.00", 4);
        String first = placeOk(guestAdd(null, clock, 1), null);
        String second = placeOk(guestAdd(null, clock, 1), null);
        String a = jdbc.queryForObject("SELECT street FROM orders WHERE id = ?", String.class, UUID.fromString(first));
        String b = jdbc.queryForObject("SELECT street FROM orders WHERE id = ?", String.class, UUID.fromString(second));
        // a fresh IV every time, so equal values don't show up as equal ciphertext
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void aChangedCiphertext_failsToRead_insteadOfShowingSomethingElse() throws Exception {
        String clock = product("65.00", 4);
        String cookie = guestAdd(null, clock, 1);
        String id = placeOk(cookie, null);
        UUID orderId = UUID.fromString(id);
        String street = jdbc.queryForObject("SELECT street FROM orders WHERE id = ?", String.class, orderId);
        // change one character in the middle of the ciphertext, as someone with database access might
        int at = street.length() / 2;
        String tampered = street.substring(0, at) + (street.charAt(at) == 'A' ? 'B' : 'A') + street.substring(at + 1);
        jdbc.update("UPDATE orders SET street = ? WHERE id = ?", tampered, orderId);

        mockMvc.perform(as(get("/orders/" + id), cookie))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("internal_error"));
    }
}
