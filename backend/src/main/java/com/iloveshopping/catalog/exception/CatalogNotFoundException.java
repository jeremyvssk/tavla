// Thrown when a catalog resource named in the URL does not exist (or is hidden); maps to 404.
package com.iloveshopping.catalog.exception;

public class CatalogNotFoundException extends RuntimeException {

    private final String code;

    public CatalogNotFoundException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
