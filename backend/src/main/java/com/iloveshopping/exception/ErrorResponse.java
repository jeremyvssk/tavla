// Structured error body; per-field messages are present only for validation failures.
package com.iloveshopping.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String error, Map<String, String> fields) {

    public ErrorResponse(String error) {
        this(error, null);
    }
}
