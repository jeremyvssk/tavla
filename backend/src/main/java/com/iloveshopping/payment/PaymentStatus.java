// Where an order's payment stands, as this app last heard from Stripe.
package com.iloveshopping.payment;

public enum PaymentStatus {
    /** a PaymentIntent exists and the shopper can pay it */
    STARTED,
    SUCCEEDED,
    /** the last attempt was declined; another card can still pay it while the hold lasts */
    FAILED,
    /** cancelled at Stripe when the order's hold ran out, so it can never be paid */
    CANCELED,
    /** the shopper cancelled a paid order; Stripe hasn't accepted the refund yet, and RefundRetry keeps asking */
    REFUND_DUE,
    /** Stripe accepted the refund and is sending the money back */
    REFUNDING,
    REFUNDED
}
