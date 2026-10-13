// The staff's order desk: the queue, moving an order along with its emails, cancelling while packing, part refunds and their limit.
package com.iloveshopping.order;

import com.iloveshopping.payment.PaymentGateway;
import com.iloveshopping.support.OrderTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StaffOrderIT extends OrderTestSupport {

    @Test
    void aPaidOrderIsPackedShippedAndDelivered_withAnEmailForEachAndTheStaffNameOnTheTimeline() throws Exception {
        Staff support = staff("SUPPORT");
        String cookie = guestAdd(null, product("30.00", 5), 1);
        String order = paidOrder(cookie);
        String number = orderNumber(order);

        mockMvc.perform(auth(get("/staff/orders"), support.token()).param("status", "PAYMENT_SUCCESSFUL").param("number", number))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(order))
                .andExpect(jsonPath("$.items[0].customerName").value("Mart Kask"))
                .andExpect(jsonPath("$.items[0].guest").value(true))
                .andExpect(jsonPath("$.items[0].shippingMethod").value("Standard courier"));

        move(support.token(), order, "{\"to\":\"PROCESSING\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.next[*]").value(hasItem("SHIPPED")))
                .andExpect(jsonPath("$.next[*]").value(hasItem("CANCELLED")))
                .andExpect(jsonPath("$.timeline[0].actor").value("Catalog Tester"));
        // the shopper's own cancel stops once packing starts
        mockMvc.perform(json(as(post("/orders/{id}/cancel", order), cookie), null, "{}")).andExpect(status().isConflict());

        move(support.token(), order, "{\"to\":\"SHIPPED\",\"carrier\":\"Omniva\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.trackingNumber").exists());
        move(support.token(), order, "{\"to\":\"SHIPPED\",\"carrier\":\"Omniva\",\"trackingNumber\":\"ce123456789ee\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracking.carrier").value("Omniva"))
                .andExpect(jsonPath("$.tracking.number").value("CE123456789EE"));
        move(support.token(), order, "{\"to\":\"DELIVERED\"}").andExpect(status().isOk()).andExpect(jsonPath("$.next.length()").value(0));

        List<SimpleMailMessage> emails = awaitEmails(number, 3);
        assertThat(emails).extracting(SimpleMailMessage::getSubject).contains(
                "Order " + number + " is on its way", "Order " + number + " was delivered");
        assertThat(emails.stream().filter(m -> m.getSubject().contains("on its way")).findFirst().orElseThrow().getText())
                .contains("CE123456789EE").contains("Standard courier");
        // the shopper's own page shows the parcel too
        mockMvc.perform(as(get("/orders/{id}", order), cookie)).andExpect(jsonPath("$.tracking.number").value("CE123456789EE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_id = ? AND actor_id = ? AND kind = 'ORDERS'",
                Integer.class, order, support.id())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT tracking_number FROM orders WHERE id = ?::uuid", String.class, order)).startsWith("v1:");
    }

    @Test
    void aMoveTheStateMachineDoesntListIsRefused() throws Exception {
        String order = paidOrder(guestAdd(null, product("30.00", 5), 1));
        move(adminToken(), order, "{\"to\":\"DELIVERED\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("order_status_conflict"));
        move(adminToken(), order, "{\"to\":\"CANCELLED\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.note").exists());
        move(adminToken(), order, "{\"to\":\"REFUNDED\"}").andExpect(status().isBadRequest());
    }

    @Test
    void staffCanCancelWhilePacking_theMoneyGoesBackAndTheStockReturns() throws Exception {
        String product = product("30.00", 5);
        String order = paidOrder(guestAdd(null, product, 2));
        String number = orderNumber(order);
        String admin = adminToken();
        move(admin, order, "{\"to\":\"PROCESSING\"}").andExpect(status().isOk());
        assertThat(stock(product)).isEqualTo(3);

        move(admin, order, "{\"to\":\"CANCELLED\",\"note\":\"Customer asked by phone\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        assertThat(stock(product)).isEqualTo(5);
        verify(gateway).refund(intentOf(order), UUID.fromString(order));
        assertThat(awaitEmails(number, 3)).extracting(SimpleMailMessage::getText)
                .anyMatch(text -> text.contains("we had to cancel your order"));
    }

    @Test
    void aPartRefundIsWorkedOutFromTheOrdersPrices_andCantGiveTheSameLineBackTwice() throws Exception {
        String board = product("40.00", 5);
        String clock = product("25.00", 5);
        String cookie = guestAdd(null, board, 2);
        guestAdd(cookie, clock, 1);
        String order = delivered(cookie);
        given(gateway.refundPart(anyString(), any(), anyLong())).willReturn(PaymentGateway.RefundStatus.SUCCEEDED);
        String admin = adminToken();

        // 105.00 is over the free-shipping limit, so there is no shipping to give back
        refund(admin, order, "[{\"productId\":\"%s\",\"quantity\":1}]".formatted(board), true, "Arrived damaged")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.shipping").exists());
        refund(admin, order, "[{\"productId\":\"%s\",\"quantity\":1}]".formatted(board), false, "Arrived damaged")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refunded").value(40.00))
                .andExpect(jsonPath("$.refundedLines['%s']".formatted(board)).value(1))
                .andExpect(jsonPath("$.status").value("DELIVERED"));
        verify(gateway).refundPart(eq(intentOf(order)), any(), eq(4000L));
        refund(admin, order, "[{\"productId\":\"%s\",\"quantity\":2}]".formatted(board), false, "Again")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.lines").value("Only 1 of %s can still be refunded."
                        .formatted(jdbc.queryForObject("SELECT name FROM products WHERE id = ?::uuid", String.class, board))));
        refund(admin, order, "[{\"productId\":\"%s\",\"quantity\":1}]".formatted(UUID.randomUUID()), false, "Not on it")
                .andExpect(status().isBadRequest());
        refund(admin, order, "[]", false, "Nothing").andExpect(status().isBadRequest());
        assertThat(stock(board)).isEqualTo(3);
        await().until(() -> emailsAbout(orderNumber(order)).stream().anyMatch(m -> m.getText().contains("We've refunded €40.00")));
        assertThat(jdbc.queryForObject("SELECT status FROM refunds WHERE order_id = ?::uuid", String.class, order)).isEqualTo("SUCCEEDED");
    }

    @Test
    void supportRefundsUpToAHundredEurosPerOrder_anAdminAnyAmount() throws Exception {
        String board = product("70.00", 5);
        String order = delivered(guestAdd(null, board, 2));
        given(gateway.refundPart(anyString(), any(), anyLong())).willReturn(PaymentGateway.RefundStatus.SUCCEEDED);
        String support = staff("SUPPORT").token();
        String line = "[{\"productId\":\"%s\",\"quantity\":1}]".formatted(board);

        refund(support, order, line, false, "Arrived damaged").andExpect(status().isOk()).andExpect(jsonPath("$.refunded").value(70.00));
        refund(support, order, line, false, "Second one too").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("refund_limit"));
        refund(adminToken(), order, line, false, "Second one too").andExpect(status().isOk()).andExpect(jsonPath("$.refunded").value(140.00));
    }

    @Test
    void stripeDownLeavesThePartRefundDue_andTheRetryFinishesIt() throws Exception {
        String board = product("30.00", 5);
        String order = delivered(guestAdd(null, board, 1));
        given(gateway.refundPart(anyString(), any(), anyLong()))
                .willThrow(new com.iloveshopping.payment.exception.PaymentUnavailableException("down", null))
                .willReturn(PaymentGateway.RefundStatus.PENDING);

        refund(adminToken(), order, "[{\"productId\":\"%s\",\"quantity\":1}]".formatted(board), false, "Missing").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM refunds WHERE order_id = ?::uuid", String.class, order)).isEqualTo("DUE");

        jdbc.update("UPDATE refunds SET updated_at = updated_at - interval '2 minutes' WHERE order_id = ?::uuid", UUID.fromString(order));
        retry.retryDueRefunds();
        assertThat(jdbc.queryForObject("SELECT status FROM refunds WHERE order_id = ?::uuid", String.class, order)).isEqualTo("PENDING");
    }

    @Test
    void startPackingMovesOnlyTheOrdersStillWaiting() throws Exception {
        String a = paidOrder(guestAdd(null, product("30.00", 5), 1));
        String b = paidOrder(guestAdd(null, product("30.00", 5), 1));
        String unpaid = placeOk(guestAdd(null, product("30.00", 5), 1), null);

        mockMvc.perform(json(post("/staff/orders/start-packing"), adminToken(), "{\"ids\":[\"%s\",\"%s\",\"%s\"]}".formatted(a, b, unpaid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moved").value(2))
                .andExpect(jsonPath("$.skipped").value(1));
        assertThat(List.of(orderStatus(a), orderStatus(b), orderStatus(unpaid))).containsExactly("PROCESSING", "PROCESSING", "PENDING_PAYMENT");
    }

    @Test
    void aTeamNoteJoinsTheTimeline_butItsTextStaysOutOfTheAuditLog() throws Exception {
        String order = paidOrder(guestAdd(null, product("30.00", 5), 1));
        mockMvc.perform(json(post("/staff/orders/{id}/notes", order), adminToken(), "{\"note\":\"Called Mart, leave at the door\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeline[0].note").value("Called Mart, leave at the door"))
                .andExpect(jsonPath("$.timeline[0].to").value(org.hamcrest.Matchers.nullValue()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_id = ? AND detail LIKE '%Mart%'", Integer.class, order))
                .isZero();
        mockMvc.perform(json(post("/staff/orders/{id}/notes", order), adminToken(), "{\"note\":\"   \"}")).andExpect(status().isBadRequest());
    }

    @Test
    void aCancelledOrderWhoseRefundIsntConfirmedAfterAnHourIsOnItsOwnTab() throws Exception {
        String order = paidOrder(guestAdd(null, product("30.00", 5), 1));
        jdbc.update("UPDATE orders SET status = 'CANCELLED' WHERE id = ?::uuid", order);
        jdbc.update("UPDATE payments SET status = 'REFUNDING', updated_at = now() - interval '2 hours' WHERE order_id = ?::uuid", order);

        mockMvc.perform(auth(get("/staff/orders"), adminToken()).param("status", "REFUND_PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(hasItem(order)))
                .andExpect(jsonPath("$.counts.REFUND_PENDING").isNumber());
    }

    @Test
    void theExportIsACsvOfTheTickedOrders() throws Exception {
        String order = paidOrder(guestAdd(null, product("30.00", 5), 1));
        String csv = mockMvc.perform(auth(get("/staff/orders/export"), adminToken()).param("ids", order))
                .andExpect(status().isOk())
                .andExpect(content().string(startsWith("﻿Order,Placed,Status,Customer,Checkout,Items,Total,Delivery,Status since\r\n")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv.split("\r\n")).hasSize(2);
        assertThat(csv).contains(orderNumber(order) + ",").contains(",Mart Kask,guest,1,");
    }

    @Test
    void salesHasNoOrderDesk_andAnUnknownStatusIsABadRequest() throws Exception {
        mockMvc.perform(auth(get("/staff/orders"), staff("SALES").token())).andExpect(status().isForbidden());
        mockMvc.perform(auth(get("/staff/orders"), adminToken()).param("status", "LOST")).andExpect(status().isBadRequest());
        mockMvc.perform(auth(get("/staff/orders/{id}", UUID.randomUUID()), adminToken())).andExpect(status().isNotFound());
    }

    @Test
    void aTotalThatChangedSinceTheCheckoutLoadedIsRefused_andNothingIsReserved() throws Exception {
        String product = product("30.00", 5);
        String cookie = guestAdd(null, product, 1);
        String stale = checkout("standard").replace("\"shippingMethod\"", "\"expectedTotal\":29.90,\"shippingMethod\"");

        assertThat(place(cookie, null, stale).getResponse().getStatus()).isEqualTo(409);
        assertThat(JsonPath.<String>read(place(cookie, null, stale).getResponse().getContentAsString(), "$.error")).isEqualTo("price_changed");
        assertThat(stock(product)).isEqualTo(5);
        String right = checkout("standard").replace("\"shippingMethod\"", "\"expectedTotal\":34.90,\"shippingMethod\"");
        assertThat(place(cookie, null, right).getResponse().getStatus()).isEqualTo(201);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private RefundRetry retry;

    private String paidOrder(String cookie) throws Exception {
        String order = placeOk(cookie, null);
        paidWith("visa", "4242");
        deliver(succeeded(order));
        assertThat(orderStatus(order)).isEqualTo("PAYMENT_SUCCESSFUL");
        return order;
    }

    private String delivered(String cookie) throws Exception {
        String order = paidOrder(cookie);
        jdbc.update("UPDATE orders SET status = 'DELIVERED' WHERE id = ?::uuid", order);
        return order;
    }

    private ResultActions move(String token, String order, String body) throws Exception {
        return mockMvc.perform(json(post("/staff/orders/{id}/status", order), token, body));
    }

    private ResultActions refund(String token, String order, String lines, boolean shipping, String reason) throws Exception {
        return mockMvc.perform(json(post("/staff/orders/{id}/refunds", order), token, """
                {"lines":%s,"shipping":%s,"reason":"%s"}""".formatted(lines, shipping, reason)));
    }
}
