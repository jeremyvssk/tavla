// What a verified Stripe webhook says about an order's payment: the message on the payment-events queue.
package com.iloveshopping.payment;

import java.time.Instant;
import java.util.UUID;

/**
 * Only ids and outcomes: no card, no name, no address. The queue is a store of its own (RabbitMQ keeps
 * durable messages on disk), and nothing in it needs protecting. The consumer asks Stripe for the
 * card's brand and last 4 itself and stores them encrypted.
 *
 * @param eventId     Stripe's event id, the key that makes a second delivery a no-op
 * @param createdAt   when Stripe created the event (whole seconds)
 * @param failureCode Stripe's decline code for a failure (insufficient_funds, expired_card...), else null
 */
public record PaymentEvent(String eventId, Kind kind, UUID orderId, Instant createdAt, String failureCode) {

    public enum Kind {
        SUCCEEDED,
        FAILED,
        /** the whole payment went back to the card (Stripe's charge.refunded) */
        REFUNDED
    }
}
