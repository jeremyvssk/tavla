// Thrown when a TOTP/backup code is wrong or a 2FA login challenge is missing or expired.
package com.iloveshopping.auth.exception;

public class InvalidTwoFactorCodeException extends RuntimeException {

    public InvalidTwoFactorCodeException() {
        super("Invalid or expired two-factor code");
    }
}
