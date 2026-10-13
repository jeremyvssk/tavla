// Authenticated caller derived from a verified access token; the Spring Security principal.
package com.iloveshopping.auth;

import com.iloveshopping.user.Role;

import java.time.Instant;
import java.util.UUID;

/**
 * Carries the access token's identity claims so authenticated handlers can act without
 * re-parsing the JWT. {@code jti} and {@code expiresAt} let logout blocklist the token
 * for its remaining lifetime. {@code mfa} is whether the session passed a second factor.
 */
public record AuthPrincipal(UUID userId, Role role, String jti, Instant expiresAt, boolean mfa) {
}
