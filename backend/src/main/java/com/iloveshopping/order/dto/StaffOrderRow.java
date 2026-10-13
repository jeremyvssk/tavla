// One row of the staff order queue: who, what, how much, and how long it has waited in its status.
package com.iloveshopping.order.dto;

import com.iloveshopping.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** shippingMethod is the delivery option's name as it was when the order was placed. */
public record StaffOrderRow(UUID id, String number, Instant placedAt, OrderStatus status, int itemCount, BigDecimal total,
                            String customerName, boolean guest, String shippingMethod, Instant statusChangedAt) {
}
