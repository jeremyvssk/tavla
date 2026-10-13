// Which staff roles may use which part of the admin, and the check SecurityConfig runs on every staff request.
package com.iloveshopping.auth;

import com.iloveshopping.auth.exception.TwoFactorRequiredException;
import com.iloveshopping.user.Role;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Set;

/**
 * Least privilege, one row per area. Support runs orders and reviews and never touches products or
 * prices; Sales runs the catalog and delivery options and never sees a customer; only Admin manages
 * people and reads the audit log. The admin pages hide what a role can't use
 * (frontend/src/admin/access.ts holds the same table), but this is the lock: a request from curl gets
 * the same answer as one from the page.
 *
 * <p>Every area also needs a second factor in the session: the access token's mfa claim. A staff member
 * signed in with a password only gets 403 two_factor_required and can reach nothing but the 2FA setup,
 * which lives under /auth/2fa and needs no role.
 */
public enum StaffArea {
    OVERVIEW(Role.SUPPORT, Role.SALES, Role.ADMIN),
    ORDERS(Role.SUPPORT, Role.ADMIN),
    REVIEWS(Role.SUPPORT, Role.ADMIN),
    CATALOG(Role.SALES, Role.ADMIN),
    DELIVERY(Role.SALES, Role.ADMIN),
    USERS(Role.ADMIN),
    AUDIT(Role.ADMIN);

    private final Set<Role> roles;

    StaffArea(Role... roles) {
        this.roles = Set.of(roles);
    }

    public boolean allows(Role role) {
        return roles.contains(role);
    }

    /**
     * Wrong role or no token: denied, which the security chain answers 401 (no token) or 403. The right
     * role without a second factor: {@link TwoFactorRequiredException}, which it answers 403
     * two_factor_required, so the admin page can say what to do instead of a bare "forbidden".
     */
    public AuthorizationManager<RequestAuthorizationContext> check() {
        return (authentication, context) -> {
            if (!(authentication.get().getPrincipal() instanceof AuthPrincipal principal) || !allows(principal.role())) {
                return new AuthorizationDecision(false);
            }
            if (!principal.mfa()) {
                throw new TwoFactorRequiredException();
            }
            return new AuthorizationDecision(true);
        };
    }
}
