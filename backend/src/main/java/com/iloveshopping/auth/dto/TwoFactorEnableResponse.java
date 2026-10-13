// Response to enabling 2FA: the one-time backup codes, shown to the user exactly once, and an access token that carries the second factor.
package com.iloveshopping.auth.dto;

import java.util.List;

/**
 * The code that switched 2FA on is a second factor, so this session has passed one: {@code accessToken}
 * says so, and a staff member can open the admin without signing in again.
 */
public record TwoFactorEnableResponse(
        List<String> backupCodes,
        String accessToken) {
}
