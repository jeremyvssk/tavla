// Thrown when a refresh token is unknown, expired, or replayed after rotation.
package com.iloveshopping.auth.exception;

public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Invalid or expired refresh token");
    }
}
