// Query parameters of GET /staff/orders and its export: which orders, in what order, which page.
package com.iloveshopping.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * status: an OrderStatus, all, or REFUND_PENDING (cancelled, and Stripe hasn't confirmed the refund for over
 * an hour). number: the order number or its start; names and emails are encrypted and never searched.
 * ids: the export's ticked rows only.
 */
public record StaffOrderParams(
        @Pattern(regexp = "all|PENDING_PAYMENT|PAYMENT_SUCCESSFUL|PAYMENT_FAILED|PROCESSING|SHIPPED|DELIVERED|CANCELLED|REFUNDED|REFUND_PENDING",
                message = "must be all, an order status or REFUND_PENDING") String status,
        @Size(max = 12) @Pattern(regexp = "[A-Za-z0-9-]*", message = "must be an order number like TV-10023") String number,
        @Pattern(regexp = "oldest|newest|total_desc", message = "must be oldest, newest or total_desc") String sort,
        LocalDate from,
        @Min(0) @Max(10_000) Integer page,
        @Min(1) @Max(100) Integer size,
        @Size(max = 500) List<UUID> ids) {
}
