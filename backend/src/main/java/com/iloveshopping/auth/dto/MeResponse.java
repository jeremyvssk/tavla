// Who the caller is: what the SPA needs after a reload, since the access token carries no PII to decode.
package com.iloveshopping.auth.dto;

import com.iloveshopping.user.AuthProvider;
import com.iloveshopping.user.Role;

import java.util.UUID;

public record MeResponse(
        UUID id,
        String email,
        String fullName,
        Role role,
        AuthProvider authProvider,
        boolean twoFactorEnabled) {
}
