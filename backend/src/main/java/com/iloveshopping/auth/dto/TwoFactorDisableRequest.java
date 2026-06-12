// Request body to disable 2FA: the account password, re-confirmed.
package com.iloveshopping.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorDisableRequest(
        @NotBlank String password) {
}
