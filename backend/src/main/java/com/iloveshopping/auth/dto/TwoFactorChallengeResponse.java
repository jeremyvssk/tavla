// Login response when 2FA is required: no tokens yet, just the challenge to complete at /auth/2fa/login.
package com.iloveshopping.auth.dto;

public record TwoFactorChallengeResponse(
        boolean twoFactorRequired,
        String challenge) {
}
