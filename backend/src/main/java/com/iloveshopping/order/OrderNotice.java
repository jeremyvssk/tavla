// The message on the order-email queue: which order needs which email.
package com.iloveshopping.order;

import java.util.UUID;

/**
 * Just the order id and the kind of email. The email itself is built when it is sent, from the order
 * in the database, so no address or name ever sits in the queue.
 */
public record OrderNotice(UUID orderId, Kind kind) {

    public enum Kind {
        /** the payment went through: the order confirmation */
        PAID,
        /** the card was declined; another card can still pay while the items are held */
        PAYMENT_FAILED,
        /** cancelled by the shopper (a refund follows) or by the timer (it was never paid) */
        CANCELLED,
        /** Stripe confirmed the money went back to the card */
        REFUNDED,
        /** staff handed it to the courier: the carrier and parcel number */
        SHIPPED,
        /** staff marked it delivered */
        DELIVERED,
        /** staff refunded part of it (some items, or the shipping); the amount is the order's latest refund */
        PART_REFUNDED
    }
}
