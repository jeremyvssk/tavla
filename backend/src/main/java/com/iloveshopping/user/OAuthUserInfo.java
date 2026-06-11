// Normalized identity from an external OAuth provider, used to find or create a user.
package com.iloveshopping.user;

public record OAuthUserInfo(
        AuthProvider provider,
        String providerId,
        String email,
        String fullName,
        String avatarUrl,
        boolean emailVerified) {
}
