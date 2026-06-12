// Request body for initiating a password reset (forgot-password).
package com.iloveshopping.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank @Email String email) {
}
