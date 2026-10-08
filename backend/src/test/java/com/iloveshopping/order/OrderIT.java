// Integration tests for orders and payments: placing from the cart, ownership, the race for the last item, webhooks and expiry.
package com.iloveshopping.order;

import com.iloveshopping.auth.OpaqueTokens;
import com.iloveshopping.payment.exception.PaymentUnavailableException;
import com.iloveshopping.support.OrderTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stripe is a mock and the mail server too (OrderTestSupport); the signature check, the queue and its
 * consumer are real. Cancelling, the order list and the queue's failure handling have classes of their own.
 */
class OrderIT extends OrderTestSupport {

    @Autowired
    OrderService orderService;

    // ---- happy path: guest and signed in ----

    @Test
    void aGuestOrder_reservesTheStock_copiesTheCart_andEmptiesIt() throws Exception {
        String board = product("24.00", 5);
        String clock = product("65.00", 3);
        String cookie = guestAdd(null, board, 2);
        guestAdd(cookie, clock, 1);

        MvcResult placed = place(cookie, null, checkout("standard"));
        assertThat(placed.getResponse().getStatus()).isEqualTo(201);
        String body = placed.getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        assertThat((String) JsonPath.read(body, "$.number")).matches("TV-\\d{5,}");
        assertThat((String) JsonPath.read(body, "$.clientSecret")).endsWith("_secret_test");
        assertThat((Object) JsonPath.read(body, "$.holdUntil")).isNotNull();
        // Stripe is asked for the server's total in cents, never anything the browser said
        verify(gateway).create(eq(UUID.fromString(id)), anyString(), eq(11300L), eq("eur"));

        assertThat(stock(board)).isEqualTo(3);
        assertThat(stock(clock)).isEqualTo(2);
        mockMvc.perform(as(get("/cart"), cookie)).andExpect(jsonPath("$.lines", hasSize(0)));

        mockMvc.perform(as(get("/orders/" + id), cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.itemCount").value(3))
                .andExpect(jsonPath("$.subtotal").value(113.00))
                .andExpect(jsonPath("$.shippingCost").value(0))
                .andExpect(jsonPath("$.total").value(113.00))
                .andExpect(jsonPath("$.vat").value(21.87))
                .andExpect(jsonPath("$.shippingMethod").value("standard"))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(24.00))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.lines[0].lineTotal").value(48.00))
                .andExpect(jsonPath("$.shipTo.street").value("Pärnu mnt 22"))
                .andExpect(jsonPath("$.cancellable").value(false))
                .andExpect(jsonPath("$.payment").value(nullValue()))
                .andExpect(jsonPath("$.history", hasSize(1)))
                .andExpect(jsonPath("$.history[0].status").value("PENDING_PAYMENT"));
    }

    @Test
    void aSignedInCheckout_goesFromCartToPaid_onlyWhenStripeSaysSo() throws Exception {
        String clock = product("65.00", 4);
        String shopper = customerToken();
        mockMvc.perform(json(post("/cart/items"), shopper, add(clock, 2))).andExpect(status().isOk());

        String id = placeOk(null, shopper);
        assertThat(orderStatus(id)).isEqualTo("PENDING_PAYMENT");

        paidWith("visa", "4242");
        deliver(succeeded(id));

        mockMvc.perform(auth(get("/orders/" + id), shopper))
                .andExpect(jsonPath("$.status").value("PAYMENT_SUCCESSFUL"))
                .andExpect(jsonPath("$.payment.brand").value("Visa"))
                .andExpect(jsonPath("$.payment.last4").value("4242"))
                .andExpect(jsonPath("$.payment.paidAt").value(notNullValue()))
                .andExpect(jsonPath("$.holdUntil").value(nullValue()))
                .andExpect(jsonPath("$.cancellable").value(true))
                .andExpect(jsonPath("$.history[0].status").value("PAYMENT_SUCCESSFUL"))
                .andExpect(jsonPath("$.history[0].note").value("Stripe confirmed the payment."));
        // paying takes nothing more: the stock was taken when the order was placed
        assertThat(stock(clock)).isEqualTo(2);
    }

    @Test
    void theOrderKeepsWhatWasPaid_whenTheCatalogChangesLater() throws Exception {
        String clock = product("65.00", 4);
        String cookie = guestAdd(null, clock, 1);
        String id = placeOk(cookie, null);

        jdbc.update("UPDATE products SET price = 99.00, name = 'Renamed' WHERE id = ?", UUID.fromString(clock));

        mockMvc.perform(as(get("/orders/" + id), cookie))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(65.00))
                .andExpect(jsonPath("$.lines[0].name").value(org.hamcrest.Matchers.startsWith("Order item")))
                .andExpect(jsonPath("$.total").value(69.90));
    }

    // ---- validation ----

    @Test
    void badFields_are400_withTheFieldAndHowToFixIt_andNothingIsReserved() throws Exception {
        String clock = product("65.00", 4);
        String cookie = guestAdd(null, clock, 1);
        String good = checkout("standard");

        record Case(String from, String to, String field, String message) {
        }
        List<Case> cases = List.of(
                new Case("\"postcode\":\"10141\"", "\"postcode\":\"1014\"", "postcode", "Estonian postcodes have 5 digits, like 10141."),
                new Case("\"email\":\"mart.kask@example.com\"", "\"email\":\"mart.kask@\"", "email", "Enter an email like name@example.com."),
                new Case("\"phone\":\"+372 5876 2210\"", "\"phone\":\"5876 2210\"", "phone", "Enter the full number with the country code, like +372 5123 4567."),
                new Case("\"street\":\"Pärnu mnt 22\"", "\"street\":\"Pärnu mnt\"", "street", "Add the house number, like Narva mnt 7."),
                new Case("\"city\":\"Tallinn\"", "\"city\":\"  \"", "city", "Enter the city."),
                new Case("\"country\":\"EE\"", "\"country\":\"SE\"", "country", "Choose a country we deliver to."),
                new Case("\"shippingMethod\":\"standard\"", "\"shippingMethod\":\"drone\"", "shippingMethod", "Choose a shipping option."));
        for (Case c : cases) {
            mockMvc.perform(json(as(post("/orders"), cookie), null, good.replace(c.from(), c.to())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("validation_failed"))
                    .andExpect(jsonPath("$.fields." + c.field()).value(c.message()));
        }
        // a Latvian postcode is fine in Latvia
        mockMvc.perform(json(as(post("/orders"), cookie), null, good.replace("\"postcode\":\"10141\"", "\"postcode\":\"LV-1050\"")
                        .replace("\"country\":\"EE\"", "\"country\":\"LV\"")))
                .andExpect(status().isCreated());
    }

    @Test
    void aPriceOrTotalInTheBody_isRefused_notUsed() throws Exception {
        String clock = product("65.00", 4);
        String cookie = guestAdd(null, clock, 1);
        String withTotal = checkout("standard").replace("\"shippingMethod\"", "\"total\":0.01,\"shippingMethod\"");
        mockMvc.perform(json(as(post("/orders"), cookie), null, withTotal)).andExpect(status().isBadRequest());
        assertThat(stock(clock)).isEqualTo(4);
    }

    // ---- auth and ownership ----

    @Test
    void anOrder_isVisibleOnlyToWhoPlacedIt() throws Exception {
        String clock = product("65.00", 4);
        String shopper = customerToken();
        mockMvc.perform(json(post("/cart/items"), shopper, add(clock, 1))).andExpect(status().isOk());
        String accountOrder = placeOk(null, shopper);
        String cookie = guestAdd(null, clock, 1);
        String guestOrder = placeOk(cookie, null);

        mockMvc.perform(auth(get("/orders/" + accountOrder), customerToken())).andExpect(status().isNotFound());
        mockMvc.perform(get("/orders/" + accountOrder)).andExpect(status().isNotFound());
        mockMvc.perform(as(get("/orders/" + guestOrder), guestAdd(null, clock, 1))).andExpect(status().isNotFound());
        mockMvc.perform(auth(get("/orders/" + guestOrder), shopper)).andExpect(status().isNotFound());
        mockMvc.perform(get("/orders/" + UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("order_not_found"));
        // paying someone else's order is the same 404
        mockMvc.perform(auth(post("/orders/" + guestOrder + "/payment"), shopper)).andExpect(status().isNotFound());
    }

    @Test
    void aRejectedToken_is401_notAGuestOrder() throws Exception {
        mockMvc.perform(json(post("/orders"), "not.a.jwt", checkout("standard")))
                .andExpect(status().isUnauthorized());
    }

    // ---- conflict and race ----

    @Test
    void anEmptyCart_is409() throws Exception {
        mockMvc.perform(json(post("/orders"), null, checkout("standard")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("cart_empty"));
    }

    @Test
    void oneLineShort_rollsBackEveryLine_andTheCartStaysAsItWas() throws Exception {
        String board = product("24.00", 5);
        String clock = product("65.00", 3);
        String cookie = guestAdd(null, board, 2);
        guestAdd(cookie, clock, 3);
        jdbc.update("UPDATE products SET stock_quantity = 1 WHERE id = ?", UUID.fromString(clock));

        mockMvc.perform(json(as(post("/orders"), cookie), null, checkout("standard")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("not_enough_stock"))
                .andExpect(jsonPath("$.productId").value(clock))
                .andExpect(jsonPath("$.maxQuantity").value(1));

        assertThat(stock(board)).as("the board's reservation was rolled back").isEqualTo(5);
        assertThat(stock(clock)).isEqualTo(1);
        mockMvc.perform(as(get("/cart"), cookie)).andExpect(jsonPath("$.lines", hasSize(2)));
    }

    @Test
    void soldOutLines_stayInTheCart_andTheRestIsOrdered() throws Exception {
        String board = product("24.00", 5);
        String gone = product("65.00", 3);
        String cookie = guestAdd(null, board, 1);
        guestAdd(cookie, gone, 1);
        jdbc.update("UPDATE products SET stock_quantity = 0 WHERE id = ?", UUID.fromString(gone));

        String id = placeOk(cookie, null);
        mockMvc.perform(as(get("/orders/" + id), cookie)).andExpect(jsonPath("$.lines", hasSize(1)));
        mockMvc.perform(as(get("/cart"), cookie))
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0].state").value("sold_out"));
    }

    @Test
    void twoBuyersForTheLastOne_exactlyOneGetsIt_andStockNeverGoesBelowZero() throws Exception {
        for (int round = 0; round < 5; round++) {
            String last = product("199.00", 1);
            String first = guestAdd(null, last, 1);
            String second = guestAdd(null, last, 1);

            CountDownLatch go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Future<Integer>> results = new ArrayList<>();
            for (String cookie : List.of(first, second)) {
                Callable<Integer> buy = () -> {
                    go.await();
                    return place(cookie, null, checkout("standard")).getResponse().getStatus();
                };
                results.add(pool.submit(buy));
            }
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get());
            }
            pool.shutdown();

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(stock(last)).isZero();
        }
    }

    @Test
    void aDoubleClickedPay_placesOneOrder() throws Exception {
        String clock = product("65.00", 10);
        String cookie = guestAdd(null, clock, 1);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return place(cookie, null, checkout("standard")).getResponse().getStatus();
            }));
        }
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(f.get());
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(stock(clock)).isEqualTo(9);
    }

    // ---- Stripe unreachable ----

    @Test
    void whenStripeCantBeReached_theOrderIsWithdrawn_andEverythingGoesBack() throws Exception {
        willThrow(new PaymentUnavailableException("timed out", null)).given(gateway).create(any(), anyString(), anyLong(), anyString());
        String clock = product("65.00", 4);
        String cookie = guestAdd(null, clock, 2);

        mockMvc.perform(json(as(post("/orders"), cookie), null, checkout("standard")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("payment_unavailable"));

        assertThat(stock(clock)).isEqualTo(4);
        mockMvc.perform(as(get("/cart"), cookie)).andExpect(jsonPath("$.lines[0].quantity").value(2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE guest_token_hash = ?", Integer.class,
                OpaqueTokens.sha256(cookie))).isZero();
    }

    // ---- the webhook ----

    @Test
    void theSameEventTwice_isAppliedOnce() throws Exception {
        String clock = product("65.00", 4);
        String cookie = guestAdd(null, clock, 1);
        String id = placeOk(cookie, null);
        paidWith("mastercard", "4444");
        String payload = succeeded(id);

        deliver(payload);
        assertThat(webhook(payload, sign(payload, WEBHOOK_SECRET)).getResponse().getStatus()).isEqualTo(200);
        drain();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_status_history WHERE order_id = ?", Integer.class,
                UUID.fromString(id))).isEqualTo(2);
        // one confirmation email, not two
        assertThat(awaitEmails(orderNumber(id), 1)).hasSize(1);
    }

    @Test
    void theSameEventTwiceAtOnce_isStillAppliedOnce() throws Exception {
        String clock = product("65.00", 4);
        String cookie = guestAdd(null, clock, 1);
        String id = placeOk(cookie, null);
        paidWith("visa", "4242");
        String payload = succeeded(id);
        String signature = sign(payload, WEBHOOK_SECRET);

        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return webhook(payload, signature).getResponse().getStatus();
            }));
        }
        go.countDown();
        for (Future<Integer> f : results) {
            assertThat(f.get()).isEqualTo(200);
        }
        pool.shutdown();
        drain();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_status_history WHERE order_id = ? AND to_status = 'PAYMENT_SUCCESSFUL'",
                Integer.class, UUID.fromString(id))).isEqualTo(1);
    }

    @Test
    void aDecline_saysWhy_keepsTheItems_andAnotherCardCanStillPay() throws Exception {
        String clock = product("65.00", 4);
        String cookie = guestAdd(null, clock, 1);
        String id = placeOk(cookie, null);

        deliver(failed(id, "insufficient_funds", Instant.now().getEpochSecond()));
        mockMvc.perform(as(get("/orders/" + id), cookie))
                .andExpect(jsonPath("$.status").value("PAYMENT_FAILED"))
                .andExpect(jsonPath("$.paymentFailure").value("Your card has insufficient funds."))
                .andExpect(jsonPath("$.holdUntil").value(notNullValue()));
        assertThat(stock(clock)).as("still held for another card").isEqualTo(3);

        mockMvc.perform(as(post("/orders/" + id + "/payment"), cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").value(matchesPattern("pi_.*_secret_test")));
        assertThat(orderStatus(id)).isEqualTo("PENDING_PAYMENT");

        // the first attempt's failure arriving late must not mark the new attempt failed
        deliver(failed(id, "insufficient_funds", Instant.now().minusSeconds(60).getEpochSecond()));
        assertThat(orderStatus(id)).isEqualTo("PENDING_PAYMENT");

        paidWith("visa", "4242");
        deliver(succeeded(id));
        mockMvc.perform(as(get("/orders/" + id), cookie))
                .andExpect(jsonPath("$.status").value("PAYMENT_SUCCESSFUL"))
                .andExpect(jsonPath("$.paymentFailure").value(nullValue()))
                .andExpect(jsonPath("$.history[1].note").value("Trying another card."));
    }

    @Test
    void theFourFailuresTheBriefNames_eachReachTheOrderPage() throws Exception {
        for (String code : List.of("insufficient_funds", "incorrect_number", "expired_card", "processing_error")) {
            String clock = product("65.00", 4);
            String cookie = guestAdd(null, clock, 1);
            String id = placeOk(cookie, null);
            deliver(failed(id, code, Instant.now().getEpochSecond()));
            String failure = JsonPath.read(mockMvc.perform(as(get("/orders/" + id), cookie)).andReturn()
                    .getResponse().getContentAsString(), "$.paymentFailure");
            assertThat(failure).as(code).isNotBlank().doesNotStartWith("Your card was declined");
        }
    }

    @Test
    void aBadOrMissingSignature_is400_andChangesNothing() throws Exception {
        String clock = product("65.00", 4);
        String cookie = guestAdd(null, clock, 1);
        String id = placeOk(cookie, null);
        String payload = succeeded(id);

        // signed for another body: someone forging a "paid" event
        assertThat(webhook(payload, sign(payload.replace("11300", "1"), WEBHOOK_SECRET)).getResponse().getStatus()).isEqualTo(400);
        assertThat(webhook(payload, sign(payload, "whsec_someone_elses")).getResponse().getStatus()).isEqualTo(400);
        assertThat(webhook(payload, null).getResponse().getStatus()).isEqualTo(400);
        assertThat(orderStatus(id)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void eventsThisShopDoesntActOn_areAcknowledged_andIgnored() throws Exception {
        // not one of our payments, and a type we don't act on: 200 so Stripe stops sending them, and never queued
        String notOurs = event("evt_" + UUID.randomUUID(), "payment_intent.succeeded", "not-a-uuid", Instant.now().getEpochSecond(), null);
        String otherType = event("evt_" + UUID.randomUUID(), "payment_intent.created", UUID.randomUUID().toString(),
                Instant.now().getEpochSecond(), null);
        for (String payload : List.of(notOurs, otherType)) {
            assertThat(webhook(payload, sign(payload, WEBHOOK_SECRET)).getResponse().getStatus()).isEqualTo(200);
        }
        // an order id this shop never had: queued, applied as nothing
        deliver(succeeded(UUID.randomUUID().toString()));
        assertThat(processed(JsonPath.read(notOurs, "$.id"))).isFalse();
        assertThat(processed(JsonPath.read(otherType, "$.id"))).isFalse();
    }

    // ---- the payment hold ----

    @Test
    void anUnpaidOrderPastItsHold_isCancelled_atStripeToo_andTheStockAndCartComeBack() throws Exception {
        String clock = product("65.00", 4);
        String cookie = guestAdd(null, clock, 2);
        String id = placeOk(cookie, null);
        assertThat(stock(clock)).isEqualTo(2);
        expireHold(id);

        orderService.settleExpiredHold(UUID.fromString(id));

        verify(gateway, atLeastOnce()).cancel("pi_" + id.replace("-", ""));
        assertThat(orderStatus(id)).isEqualTo("CANCELLED");
        assertThat(stock(clock)).isEqualTo(4);
        mockMvc.perform(as(get("/cart"), cookie)).andExpect(jsonPath("$.lines[0].quantity").value(2));
        mockMvc.perform(as(get("/orders/" + id), cookie))
                .andExpect(jsonPath("$.history[0].note").value("Not paid within 30 minutes, so the items went back on sale."))
                .andExpect(jsonPath("$.holdUntil").value(nullValue()));
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, UUID.fromString(id)))
                .isEqualTo("CANCELED");
    }

    @Test
    void anExpiredHold_thatStripeSaysWasPaid_isMarkedPaid_notReleased() throws Exception {
        String clock = product("65.00", 4);
        String cookie = guestAdd(null, clock, 1);
        String id = placeOk(cookie, null);
        expireHold(id);
        paidWith("visa", "4242");

        orderService.settleExpiredHold(UUID.fromString(id));

        assertThat(orderStatus(id)).isEqualTo("PAYMENT_SUCCESSFUL");
        assertThat(stock(clock)).isEqualTo(3);
    }

    @Test
    void anExpiredHold_staysPut_whileStripeCantBeAsked() throws Exception {
        String clock = product("65.00", 4);
        String cookie = guestAdd(null, clock, 1);
        String id = placeOk(cookie, null);
        expireHold(id);
        willThrow(new PaymentUnavailableException("timed out", null)).given(gateway).lookUp(anyString());

        orderService.settleExpiredHold(UUID.fromString(id));

        assertThat(orderStatus(id)).isEqualTo("PENDING_PAYMENT");
        assertThat(stock(clock)).isEqualTo(3);
    }

    @Test
    void payingAgain_isRefused_afterTheHold_orOnceItIsPaid() throws Exception {
        String clock = product("65.00", 4);
        String cookie = guestAdd(null, clock, 1);
        String expired = placeOk(cookie, null);
        deliver(failed(expired, "expired_card", Instant.now().getEpochSecond()));
        expireHold(expired);
        mockMvc.perform(as(post("/orders/" + expired + "/payment"), cookie))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("hold_expired"));

        guestAdd(cookie, clock, 1);
        String paid = placeOk(cookie, null);
        paidWith("visa", "4242");
        deliver(succeeded(paid));
        mockMvc.perform(as(post("/orders/" + paid + "/payment"), cookie))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("order_not_payable"));
    }
}
