// Thrown when an OAuth sign-in's email already belongs to an existing account.
// We never silently link identities, so the user is told to log in another way.
package com.iloveshopping.user.exception;

public class EmailRegisteredWithPasswordException extends RuntimeException {

    public EmailRegisteredWithPasswordException() {
        super("Email is already registered with a password");
    }
}
