// Thrown when an address id isn't one of the signed-in account's own; maps to 404 address_not_found.
package com.iloveshopping.user.exception;

public class AddressNotFoundException extends RuntimeException {

    public AddressNotFoundException() {
        super("address_not_found");
    }
}
