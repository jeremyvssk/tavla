// Request body for Google OAuth login: the ID token obtained by the SPA from Google.
package com.iloveshopping.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(@NotBlank String idToken) {
}
