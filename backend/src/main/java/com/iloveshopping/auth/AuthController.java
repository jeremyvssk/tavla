// REST endpoints for register/login/refresh/logout/me; refresh token travels as an httpOnly cookie.
package com.iloveshopping.auth;

import com.iloveshopping.auth.dto.GoogleLoginRequest;
import com.iloveshopping.auth.dto.LoginRequest;
import com.iloveshopping.auth.dto.MeResponse;
import com.iloveshopping.auth.dto.RegisterRequest;
import com.iloveshopping.auth.dto.RegisterResponse;
import com.iloveshopping.auth.dto.TokenResponse;
import com.iloveshopping.auth.dto.TwoFactorChallengeResponse;
import com.iloveshopping.auth.dto.TwoFactorLoginRequest;
import com.iloveshopping.auth.exception.InvalidRefreshTokenException;
import com.iloveshopping.user.User;
import com.iloveshopping.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/auth")
public class AuthController {

    static final String REFRESH_COOKIE = "refresh_token";
    private static final String COOKIE_PATH = "/auth";

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final UserService userService;
    // Browsers exempt localhost from the Secure rule but nothing else: over plain http on a LAN
    // address the cookie is dropped silently and every refresh 401s. Defaults to true so a missing
    // setting fails closed; dev compose is the only place that turns it off.
    private final boolean cookieSecure;

    public AuthController(AuthService authService, CaptchaService captchaService, UserService userService,
                          @Value("${app.cookie.secure}") boolean cookieSecure) {
        this.authService = authService;
        this.captchaService = captchaService;
        this.userService = userService;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        captchaService.verify(request.captchaToken());
        User user = authService.register(request.email(), request.password(), request.fullName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse(user.getId(), user.getEmail()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        LoginResult result = authService.login(request.email(), request.password(), http.getRemoteAddr());
        if (result.twoFactorRequired()) {
            // Password was correct but 2FA is on: hand back a challenge, no tokens or cookie yet.
            return ResponseEntity.ok(new TwoFactorChallengeResponse(true, result.twoFactorChallenge()));
        }
        return tokenResponse(result.tokens());
    }

    @PostMapping("/2fa/login")
    public ResponseEntity<TokenResponse> twoFactorLogin(@Valid @RequestBody TwoFactorLoginRequest request) {
        AuthTokens tokens = authService.twoFactorLogin(request.challenge(), request.code());
        return tokenResponse(tokens);
    }

    @PostMapping("/oauth/google")
    public ResponseEntity<TokenResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        AuthTokens tokens = authService.oauthLoginGoogle(request.idToken());
        return tokenResponse(tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new InvalidRefreshTokenException();
        }
        AuthTokens tokens = authService.refresh(refreshToken);
        return tokenResponse(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthPrincipal principal,
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(principal, refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedRefreshCookie().toString())
                .build();
    }

    // Read from the database rather than the token, so a role or 2FA change shows up immediately.
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        User user = userService.getById(principal.userId());
        return new MeResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getAuthProvider(), user.isTwoFactorEnabled());
    }

    private ResponseEntity<TokenResponse> tokenResponse(AuthTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken()).toString())
                .body(new TokenResponse(tokens.accessToken()));
    }

    private ResponseCookie refreshCookie(String value) {
        return baseCookie(value).maxAge(authService.refreshTokenTtl()).build();
    }

    private ResponseCookie clearedRefreshCookie() {
        return baseCookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(COOKIE_PATH);
    }
}
