// Thrown when 2FA enrolment is attempted on an account that already has 2FA switched on.
package com.iloveshopping.auth.exception;

public class TwoFactorAlreadyEnabledException extends RuntimeException {

    public TwoFactorAlreadyEnabledException() {
        super("Two-factor authentication is already enabled");
    }
}
