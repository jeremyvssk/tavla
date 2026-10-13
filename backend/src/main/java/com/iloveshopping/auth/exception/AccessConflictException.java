// A rule about roles and 2FA that a request would break (a staff account turning 2FA off, changing your own role); maps to 409.
package com.iloveshopping.auth.exception;

public class AccessConflictException extends RuntimeException {

    private final String code;

    public AccessConflictException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
