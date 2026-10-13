// Thrown when fields pass their own annotations but fail a rule across fields; maps to 400 validation_failed.
package com.iloveshopping.exception;

import java.util.Map;

/** Same body as an annotation failure: {"error":"validation_failed","fields":{...}}, so the page marks the field. */
public class FieldValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public FieldValidationException(Map<String, String> fields) {
        super("validation_failed");
        this.fields = Map.copyOf(fields);
    }

    public FieldValidationException(String field, String message) {
        this(Map.of(field, message));
    }

    public Map<String, String> getFields() {
        return fields;
    }
}
