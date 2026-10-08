// Scheduled job that asks Stripe again for refunds it couldn't be asked for when the order was cancelled.
package com.iloveshopping.order;

import com.iloveshopping.payment.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * The cancellation commits first and asks Stripe for the refund right after. If Stripe was down then,
 * or the server stopped in between, the payment is left REFUND_DUE: the database itself is the to-do
 * list, and this job works through it. Waiting a minute keeps it from racing the cancel request's own
 * attempt; if they ever do meet, the refund's idempotency key makes Stripe refund once.
 */
@Component
public class RefundRetry {

    private static final Logger log = LoggerFactory.getLogger(RefundRetry.class);
    private static final Duration WAIT = Duration.ofMinutes(1);
    private static final int BATCH = 20;

    private final PaymentService paymentService;
    private final OrderService orderService;

    public RefundRetry(PaymentService paymentService, OrderService orderService) {
        this.paymentService = paymentService;
        this.orderService = orderService;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    public void retryDueRefunds() {
        for (UUID orderId : paymentService.refundsDue(WAIT, BATCH)) {
            try {
                orderService.requestRefund(orderId);
            } catch (RuntimeException e) {
                log.warn("Could not ask for the refund of order {}; trying again next run", orderId, e);
            }
        }
    }
}
