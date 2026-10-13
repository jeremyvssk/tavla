// Thrown when Support refunds past the most they may refund on one order; maps to 403 refund_limit.
package com.iloveshopping.order.exception;

public class RefundLimitException extends RuntimeException {

    public RefundLimitException() {
        super("refund_limit");
    }
}
