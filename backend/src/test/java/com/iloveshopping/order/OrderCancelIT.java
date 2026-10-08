// Integration tests for cancelling an order: who may, until when, the stock coming back, and the Stripe refund.
package com.iloveshopping.order;

import com.iloveshopping.payment.PaymentGateway;
import com.iloveshopping.payment.exception.PaymentUnavailableException;
import com.iloveshopping.support.OrderTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The mock gateway refunds at once unless a test says otherwise, like Stripe does for test cards. */
class OrderCancelIT extends OrderTestSupport {

    @Autowired
    RefundRetry refundRetry;

    /** A signed-in shopper's order for 2 of a €65 product (stock 5), paid with Visa 4242. */
    private record Paid(String token, String productId, String orderId) {
    }

    private Paid paidOrder() throws Exception {
        String clock = product("65.00", 5);
        String token = customerToken();
        userAdd(token, clock, 2);
        String id = placeOk(null, token);
        paidWith("visa", "4242");
        deliver(succeeded(id));
        assertThat(orderStatus(id)).isEqualTo("PAYMENT_SUCCESSFUL");
        return new Paid(token, clock, id);
    }

    private MockHttpServletRequestBuilder cancel(String orderId, String reason) {
        return post("/orders/" + orderId + "/cancel").contentType("application/json")
                .content(reason == null ? "{\"reason\":null}" : "{\"reason\":\"" + reason + "\"}");
    }

    private String paymentStatus(String orderId) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, UUID.fromString(orderId));
    }

    // ---- happy path ----

    @Test
    void cancellingAPaidOrder_putsTheStockBack_refundsTheCard_andSaysSoInTheHistory() throws Exception {
        Paid o = paidOrder();
        assertThat(stock(o.productId())).isEqualTo(3);

        mockMvc.perform(auth(cancel(o.orderId(), "Changed my mind"), o.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.cancellable").value(false))
                .andExpect(jsonPath("$.history[0].status").value("REFUNDED"))
                .andExpect(jsonPath("$.history[0].note").value("Stripe refunded the payment."))
                .andExpect(jsonPath("$.history[1].status").value("CANCELLED"))
                .andExpect(jsonPath("$.history[1].note").value("Cancelled by you: Changed my mind. The refund of €130.00 has been started."));

        assertThat(stock(o.productId())).as("back on sale").isEqualTo(5);
        verify(gateway).refund(intentOf(o.orderId()), UUID.fromString(o.orderId()));
        assertThat(paymentStatus(o.orderId())).isEqualTo("REFUNDED");
        // cancelling is not "try again later": the items don't go back into the cart
        mockMvc.perform(auth(get("/cart"), o.token())).andExpect(jsonPath("$.lines", hasSize(0)));

        List<SimpleMailMessage> emails = awaitEmails(orderNumber(o.orderId()), 3);
        assertThat(emails).extracting(SimpleMailMessage::getSubject).containsExactly(
                "Order " + orderNumber(o.orderId()) + " is confirmed",
                "Order " + orderNumber(o.orderId()) + " is cancelled",
                "Refund for order " + orderNumber(o.orderId()) + " is on its way");
        assertThat(emails.get(1).getText()).contains("€130.00 back to Visa ending 4242");
    }

    @Test
    void aRefundStripeConfirmsLater_isFinishedByItsWebhook() throws Exception {
        given(gateway.refund(anyString(), any())).willReturn(PaymentGateway.RefundStatus.PENDING);
        Paid o = paidOrder();

        mockMvc.perform(auth(cancel(o.orderId(), null), o.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.history[0].note").value("Cancelled by you. The refund of €130.00 has been started."));
        assertThat(paymentStatus(o.orderId())).isEqualTo("REFUNDING");

        // a partial refund made by hand in the dashboard isn't the order's refund: acknowledged, never queued
        String partial = refunded(o.orderId(), false);
        assertThat(webhook(partial, sign(partial, WEBHOOK_SECRET)).getResponse().getStatus()).isEqualTo(200);
        drain();
        assertThat(orderStatus(o.orderId())).isEqualTo("CANCELLED");

        deliver(refunded(o.orderId(), true));
        assertThat(orderStatus(o.orderId())).isEqualTo("REFUNDED");
        assertThat(paymentStatus(o.orderId())).isEqualTo("REFUNDED");
        mockMvc.perform(auth(get("/orders/" + o.orderId()), o.token()))
                .andExpect(jsonPath("$.history[0].note").value("Stripe confirmed the refund."));
    }

    @Test
    void whenStripeIsDown_theOrderIsStillCancelled_andTheRefundIsAskedForAgainLater() throws Exception {
        Paid o = paidOrder();
        willThrow(new PaymentUnavailableException("timed out", null)).given(gateway).refund(anyString(), any());

        mockMvc.perform(auth(cancel(o.orderId(), null), o.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(paymentStatus(o.orderId())).isEqualTo("REFUND_DUE");
        assertThat(stock(o.productId())).isEqualTo(5);

        // too recent for the job: the cancel request's own attempt may still be running
        willReturn(PaymentGateway.RefundStatus.SUCCEEDED).given(gateway).refund(anyString(), any());
        refundRetry.retryDueRefunds();
        assertThat(orderStatus(o.orderId())).isEqualTo("CANCELLED");

        jdbc.update("UPDATE payments SET updated_at = updated_at - interval '2 minutes' WHERE order_id = ?", UUID.fromString(o.orderId()));
        refundRetry.retryDueRefunds();
        assertThat(orderStatus(o.orderId())).isEqualTo("REFUNDED");
        verify(gateway, times(2)).refund(intentOf(o.orderId()), UUID.fromString(o.orderId()));
    }

    // ---- the rule: only paid, only before packing ----

    @Test
    void cancelling_isRefused_oncePackingStarted_orBeforeItWasPaid() throws Exception {
        Paid o = paidOrder();
        jdbc.update("UPDATE orders SET status = 'PROCESSING' WHERE id = ?", UUID.fromString(o.orderId()));
        mockMvc.perform(auth(cancel(o.orderId(), null), o.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("order_not_cancellable"));
        assertThat(stock(o.productId())).isEqualTo(3);

        String clock = product("65.00", 5);
        String cookie = guestAdd(null, clock, 1);
        String unpaid = placeOk(cookie, null);
        mockMvc.perform(as(cancel(unpaid, null), cookie))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("order_not_cancellable"));
        verify(gateway, never()).refund(anyString(), any());
    }

    @Test
    void aSecondCancel_changesNothing_andRefundsOnce() throws Exception {
        Paid o = paidOrder();
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return mockMvc.perform(auth(cancel(o.orderId(), null), o.token())).andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        for (Future<Integer> f : results) {
            assertThat(f.get()).isEqualTo(200);
        }
        pool.shutdown();

        assertThat(stock(o.productId())).as("given back once").isEqualTo(5);
        verify(gateway, times(1)).refund(eq(intentOf(o.orderId())), any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_status_history WHERE order_id = ? AND to_status = 'CANCELLED'",
                Integer.class, UUID.fromString(o.orderId()))).isEqualTo(1);
    }

    // ---- who ----

    @Test
    void onlyWhoPlacedTheOrder_canCancelIt() throws Exception {
        Paid o = paidOrder();
        mockMvc.perform(auth(cancel(o.orderId(), null), customerToken())).andExpect(status().isNotFound());
        mockMvc.perform(cancel(o.orderId(), null)).andExpect(status().isNotFound());
        mockMvc.perform(as(cancel(o.orderId(), null), guestAdd(null, o.productId(), 1))).andExpect(status().isNotFound());
        assertThat(orderStatus(o.orderId())).isEqualTo("PAYMENT_SUCCESSFUL");

        // a guest cancels their own with the cart cookie it was placed with
        String clock = product("65.00", 5);
        String cookie = guestAdd(null, clock, 1);
        String guestOrder = placeOk(cookie, null);
        deliver(succeeded(guestOrder));
        mockMvc.perform(as(cancel(guestOrder, null), cookie)).andExpect(status().isOk());
        assertThat(orderStatus(guestOrder)).isEqualTo("REFUNDED");
    }

    // ---- validation ----

    @Test
    void aReason_isShortAndOneLine() throws Exception {
        Paid o = paidOrder();
        mockMvc.perform(auth(cancel(o.orderId(), "x".repeat(101)), o.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.reason").value("Keep the reason under 100 characters."));
        mockMvc.perform(auth(cancel(o.orderId(), "line one\\nline two"), o.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.reason").value("Keep the reason to one line of text."));
        // no body at all is fine: the reason is optional
        mockMvc.perform(auth(post("/orders/" + o.orderId() + "/cancel"), o.token())).andExpect(status().isOk());
    }
}
