// One order as staff see it: everything the shopper sees, plus the customer, the full timeline, refunds and the next moves.
package com.iloveshopping.order.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.iloveshopping.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The shopper's view is unwrapped into this one, so the page reads the same fields from both. timeline is
 * newest first: every status move with who made it (SHOPPER, STRIPE, TIMER, or a staff member's name), and
 * the team's notes, whose {@code to} is null. refundedLines: per product id, how many were refunded.
 * next: the moves staff may make from here; the server's state machine decides.
 */
public record StaffOrderDetailResponse(
        @JsonUnwrapped OrderDetailResponse order,
        String customerName,
        boolean guest,
        List<TimelineEntry> timeline,
        BigDecimal refunded,
        Map<String, Integer> refundedLines,
        boolean shippingRefunded,
        List<OrderStatus> next) {

    public record TimelineEntry(Instant at, OrderStatus from, OrderStatus to, String actor, String note) {
    }
}
