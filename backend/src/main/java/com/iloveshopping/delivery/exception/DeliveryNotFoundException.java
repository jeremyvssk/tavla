// Thrown when a staff request names a delivery option that doesn't exist; maps to 404 delivery_option_not_found.
package com.iloveshopping.delivery.exception;

public class DeliveryNotFoundException extends RuntimeException {

    public DeliveryNotFoundException() {
        super("delivery_option_not_found");
    }
}
