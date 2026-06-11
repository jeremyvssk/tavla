// Thrown when a third-party OAuth ID token fails verification (bad signature, expiry, audience).
package com.iloveshopping.auth.exception;

public class InvalidOAuthTokenException extends RuntimeException {

    public InvalidOAuthTokenException() {
        super("Invalid OAuth token");
    }
}
