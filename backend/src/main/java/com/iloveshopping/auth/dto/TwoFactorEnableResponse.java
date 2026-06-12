// Response to enabling 2FA: the one-time backup codes, shown to the user exactly once.
package com.iloveshopping.auth.dto;

import java.util.List;

public record TwoFactorEnableResponse(
        List<String> backupCodes) {
}
