// Thrown when a request body points at an id that doesn't exist; reported as a field validation error.
package com.iloveshopping.catalog.exception;

public class InvalidReferenceException extends RuntimeException {

    private final String field;

    public InvalidReferenceException(String field) {
        super(field + " does not exist");
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
