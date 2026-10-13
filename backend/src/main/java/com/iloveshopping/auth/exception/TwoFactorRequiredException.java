// A staff request from a session that hasn't passed a second factor; answered 403 two_factor_required.
package com.iloveshopping.auth.exception;

import org.springframework.security.access.AccessDeniedException;

/** An AccessDeniedException, so the security chain's own 403 handler answers it (SecurityConfig). */
public class TwoFactorRequiredException extends AccessDeniedException {

    public TwoFactorRequiredException() {
        super("A second factor is required for staff pages");
    }
}
