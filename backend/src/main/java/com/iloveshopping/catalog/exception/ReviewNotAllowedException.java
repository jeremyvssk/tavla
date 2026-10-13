// Thrown when a shopper may not do this to a review (no delivered order yet, their own review); maps to 403 with the code.
package com.iloveshopping.catalog.exception;

public class ReviewNotAllowedException extends RuntimeException {

    private final String code;

    public ReviewNotAllowedException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
