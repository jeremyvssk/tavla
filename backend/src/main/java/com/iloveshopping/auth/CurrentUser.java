// Who is making the current request, for the few rules that depend on the caller's role inside a service.
package com.iloveshopping.auth;

import com.iloveshopping.user.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Most role rules live in SecurityConfig and never reach a service. This is for the rules that depend on
 * the request's content as well as the role, like how far Sales may move a price.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** Null outside a signed-in request: a scheduled job, or a test calling the service directly. */
    public static AuthPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthPrincipal p ? p : null;
    }

    public static Role role() {
        AuthPrincipal p = principal();
        return p == null ? null : p.role();
    }
}
