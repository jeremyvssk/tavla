// Scheduled job that asks Stripe again for refunds it couldn't be asked for at the time: a cancelled order's, or a part refund.
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
    private final StaffOrderService staffOrderService;

    public RefundRetry(PaymentService paymentService, OrderService orderService, StaffOrderService staffOrderService) {
        this.paymentService = paymentService;
        this.orderService = orderService;
        this.staffOrderService = staffOrderService;
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
        // part refunds made by staff (StaffOrderService.refund) are the same kind of to-do list, in their own table
        for (Refund refund : staffOrderService.refundsDue(WAIT, BATCH)) {
            try {
                staffOrderService.askStripe(refund.getId(), refund.getOrderId(), refund.getAmount());
            } catch (RuntimeException e) {
                log.warn("Could not ask for part refund {}; trying again next run", refund.getId(), e);
            }
        }
    }
}
