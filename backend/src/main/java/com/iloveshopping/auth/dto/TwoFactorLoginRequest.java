// Request body for the second login step: the challenge from /auth/login plus a TOTP or backup code.
package com.iloveshopping.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorLoginRequest(
        @NotBlank String challenge,
        @NotBlank String code) {
}
