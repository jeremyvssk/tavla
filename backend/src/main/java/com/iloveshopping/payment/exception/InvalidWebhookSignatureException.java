// Thrown when a webhook's Stripe-Signature doesn't match the body and the webhook secret; maps to 400.
package com.iloveshopping.payment.exception;

/** The request may be someone forging a "paid" event, so nothing in it is acted on. */
public class InvalidWebhookSignatureException extends RuntimeException {

    public InvalidWebhookSignatureException() {
        super("invalid_signature");
    }
}
