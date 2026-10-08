// Thrown when checkout fields pass their own annotations but fail a rule across fields (postcode for the country); maps to 400.
package com.iloveshopping.order.exception;

import java.util.Map;

/** Same body as an annotation failure: {"error":"validation_failed","fields":{...}}. */
public class OrderValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public OrderValidationException(Map<String, String> fields) {
        super("validation_failed");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> getFields() {
        return fields;
    }
}
