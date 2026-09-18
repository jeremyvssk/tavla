// Thrown when a write collides with a uniqueness rule (slug taken, review already posted); maps to 409.
package com.iloveshopping.catalog.exception;

public class CatalogConflictException extends RuntimeException {

    private final String code;

    public CatalogConflictException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
