// 201 body of POST /orders: the new order, and the secret the browser confirms its Stripe payment with.
package com.iloveshopping.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code clientSecret} lets Stripe.js confirm this one payment and nothing else; it goes only to the
 * shopper who placed the order and is never stored or logged here. {@code holdUntil}: the items are
 * reserved until then.
 */
public record PlacedOrderResponse(UUID id, String number, BigDecimal total, Instant holdUntil, String clientSecret) {
}
