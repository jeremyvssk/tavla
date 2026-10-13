// Thrown when Sales changes a price by more than the share it may change alone; maps to 403 price_change_too_large.
package com.iloveshopping.catalog.exception;

public class PriceChangeTooLargeException extends RuntimeException {

    public PriceChangeTooLargeException() {
        super("price_change_too_large");
    }
}
