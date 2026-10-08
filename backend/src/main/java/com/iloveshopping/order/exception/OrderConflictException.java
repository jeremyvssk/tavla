// Thrown when the order or the cart is in the wrong state for the request; maps to 409 with the code.
package com.iloveshopping.order.exception;

/**
 * Codes: "cart_empty" (nothing to order), "order_status_conflict" (a status move the table doesn't
 * allow), "order_not_payable" (already paid, cancelled...), "hold_expired" (the items went back on sale),
 * "order_not_cancellable" (packing has started, or it was never paid).
 */
public class OrderConflictException extends RuntimeException {

    private final String code;

    public OrderConflictException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
