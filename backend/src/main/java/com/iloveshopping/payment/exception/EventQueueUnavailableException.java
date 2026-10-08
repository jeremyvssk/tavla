// Thrown when a verified webhook can't be put on the queue; maps to 503 so Stripe sends it again later.
package com.iloveshopping.payment.exception;

public class EventQueueUnavailableException extends RuntimeException {

    public EventQueueUnavailableException(Throwable cause) {
        super("The payment-events queue did not accept the message", cause);
    }
}
