// Scheduled job that releases the stock of orders not paid within the hold (app.orders.payment-hold).
package com.iloveshopping.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Each order is handled on its own, so one that Stripe can't answer for right now waits for the next
 * run without holding up the rest. Every instance may run this at once: OrderService re-checks each
 * order under a row lock before releasing anything.
 */
@Component
public class OrderExpiry {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiry.class);
    private static final int BATCH = 50;

    private final OrderRepository orders;
    private final OrderService orderService;

    public OrderExpiry(OrderRepository orders, OrderService orderService) {
        this.orders = orders;
        this.orderService = orderService;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    public void releaseExpiredHolds() {
        for (UUID id : orders.findExpiredHolds(Instant.now(), BATCH)) {
            try {
                orderService.settleExpiredHold(id);
            } catch (RuntimeException e) {
                log.warn("Could not settle the expired hold of order {}; trying again next run", id, e);
            }
        }
    }
}
