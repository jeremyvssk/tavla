// Thrown when an order doesn't exist or belongs to someone else; maps to 404 either way.
package com.iloveshopping.order.exception;

/** Someone else's order answers exactly like a missing one, so an order id can't be probed for. */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException() {
        super("order_not_found");
    }
}
