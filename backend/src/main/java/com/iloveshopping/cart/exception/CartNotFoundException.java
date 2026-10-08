// Thrown when the product or cart line named in the request does not exist; maps to 404.
package com.iloveshopping.cart.exception;

public class CartNotFoundException extends RuntimeException {

    private final String code;

    public CartNotFoundException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
