// Request body to enable 2FA: the first TOTP code proving the authenticator is set up.
package com.iloveshopping.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorEnableRequest(
        @NotBlank String code) {
}
