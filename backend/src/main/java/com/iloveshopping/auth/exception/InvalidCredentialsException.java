// Thrown when an email/password login fails; message is deliberately non-specific.
package com.iloveshopping.auth.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
