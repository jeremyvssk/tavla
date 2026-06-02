// Thrown when registering an email that is already taken.
package com.iloveshopping.user.exception;

public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException() {
        super("Email already registered");
    }
}
