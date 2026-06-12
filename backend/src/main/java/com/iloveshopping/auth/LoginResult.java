// Outcome of a password login: either issued tokens, or a pending 2FA challenge to complete.
package com.iloveshopping.auth;

record LoginResult(AuthTokens tokens, String twoFactorChallenge) {

    static LoginResult authenticated(AuthTokens tokens) {
        return new LoginResult(tokens, null);
    }

    static LoginResult twoFactorRequired(String challenge) {
        return new LoginResult(null, challenge);
    }

    boolean twoFactorRequired() {
        return twoFactorChallenge != null;
    }
}
