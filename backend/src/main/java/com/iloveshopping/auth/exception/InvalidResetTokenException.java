// Thrown when a password-reset token is unknown, expired, or already used.
package com.iloveshopping.auth.exception;

public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException() {
        super("Invalid or expired password reset token");
    }
}
