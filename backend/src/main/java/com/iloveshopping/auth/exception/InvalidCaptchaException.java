// Thrown when a registration request fails reCAPTCHA verification.
package com.iloveshopping.auth.exception;

public class InvalidCaptchaException extends RuntimeException {

    public InvalidCaptchaException() {
        super("CAPTCHA verification failed");
    }
}
