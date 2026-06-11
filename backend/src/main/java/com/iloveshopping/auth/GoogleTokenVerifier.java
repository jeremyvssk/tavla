// Verifies a Google ID token's signature/claims and extracts the user's identity.
package com.iloveshopping.auth;

import com.iloveshopping.auth.exception.InvalidOAuthTokenException;
import com.iloveshopping.user.AuthProvider;
import com.iloveshopping.user.OAuthUserInfo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

@Service
public class GoogleTokenVerifier {

    private final JwtDecoder googleIdTokenDecoder;

    public GoogleTokenVerifier(@Qualifier("googleIdTokenDecoder") JwtDecoder googleIdTokenDecoder) {
        this.googleIdTokenDecoder = googleIdTokenDecoder;
    }

    /**
     * Decodes and validates the ID token (signature, issuer, audience, expiry via the decoder's
     * validators) and maps its claims to our identity model. Any verification failure becomes a 401.
     */
    public OAuthUserInfo verify(String idToken) {
        Jwt jwt;
        try {
            jwt = googleIdTokenDecoder.decode(idToken);
        } catch (JwtException e) {
            throw new InvalidOAuthTokenException();
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new InvalidOAuthTokenException();
        }

        // "name" requires the profile scope; fall back to the email so full_name (NOT NULL) is always set.
        String fullName = jwt.getClaimAsString("name");
        if (fullName == null || fullName.isBlank()) {
            fullName = email;
        }

        return new OAuthUserInfo(
                AuthProvider.GOOGLE,
                jwt.getSubject(),
                email,
                fullName,
                jwt.getClaimAsString("picture"),
                Boolean.TRUE.equals(jwt.getClaim("email_verified")));
    }
}
