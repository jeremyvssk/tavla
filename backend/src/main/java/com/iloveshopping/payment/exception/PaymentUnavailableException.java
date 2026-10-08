// Thrown when Stripe can't be reached or refuses a call, or no Stripe key is set; maps to 503.
package com.iloveshopping.payment.exception;

/**
 * A timeout lands here too, and then we don't know whether Stripe did the work. That is safe for
 * every call this app makes: creating a PaymentIntent moves no money (only the browser can confirm
 * it), and the idempotency key makes a retry return the same one.
 */
public class PaymentUnavailableException extends RuntimeException {

    public PaymentUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
