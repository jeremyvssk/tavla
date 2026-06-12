// Response to 2FA setup: the shared secret and the otpauth:// URI for QR-code enrollment.
package com.iloveshopping.auth.dto;

public record TwoFactorSetupResponse(
        String secret,
        String otpauthUri) {
}
