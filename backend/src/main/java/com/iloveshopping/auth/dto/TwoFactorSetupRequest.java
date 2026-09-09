// Request body for starting 2FA enrolment; the password re-confirms the account owner.
package com.iloveshopping.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TwoFactorSetupRequest(
        @NotBlank @Size(max = 72) String password) {
}
