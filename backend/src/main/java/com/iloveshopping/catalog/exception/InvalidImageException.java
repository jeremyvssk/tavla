// Thrown when an upload is not an image we accept (wrong type, too large, undecodable); maps to 400.
package com.iloveshopping.catalog.exception;

public class InvalidImageException extends RuntimeException {

    private final String code;

    public InvalidImageException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
