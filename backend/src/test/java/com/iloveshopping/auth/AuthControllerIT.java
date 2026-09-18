// Integration tests for the auth endpoints against real Postgres + Redis (Testcontainers).
package com.iloveshopping.auth;

import com.iloveshopping.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIT extends AbstractIntegrationTest {

    // Replaces the real Google decoder so tests never call Google; we stub the decoded claims.
    @MockitoBean
    JwtDecoder googleIdTokenDecoder;

    @Test
    void register_succeeds_thenDuplicateEmailConflicts() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(register(email, "password123", "Test User"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.id").exists());

        mockMvc.perform(register(email, "password123", "Test User"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("email_already_exists"));
    }

    @Test
    void register_withInvalidBody_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(register("not-an-email", "short", "Test User"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void login_withValidCredentials_returnsAccessTokenAndRefreshCookie() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email, "password123", "Test User")).andExpect(status().isCreated());

        mockMvc.perform(login(email, "password123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(cookie().exists(AuthController.REFRESH_COOKIE))
                .andExpect(cookie().httpOnly(AuthController.REFRESH_COOKIE, true));
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email, "password123", "Test User")).andExpect(status().isCreated());

        mockMvc.perform(login(email, "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    void refresh_rotatesToken_andReplayedCookieIsRejected() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email, "password123", "Test User")).andExpect(status().isCreated());
        String firstRefresh = refreshCookieValue(mockMvc.perform(login(email, "password123")).andReturn());

        // First use rotates successfully and issues a new cookie.
        MvcResult refreshed = mockMvc.perform(post("/auth/refresh").cookie(refreshCookie(firstRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        String secondRefresh = refreshCookieValue(refreshed);
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        // Replaying the now-rotated first token must be rejected (single-use).
        mockMvc.perform(post("/auth/refresh").cookie(refreshCookie(firstRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_blocklistsAccessToken_soItStopsWorking() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email, "password123", "Test User")).andExpect(status().isCreated());
        MvcResult loginResult = mockMvc.perform(login(email, "password123")).andReturn();
        String accessToken = accessToken(loginResult);
        Cookie refresh = refreshCookie(refreshCookieValue(loginResult));

        // A valid access token authorizes logout, which revokes it.
        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)).cookie(refresh))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(AuthController.REFRESH_COOKIE, 0));

        // The same token is now blocklisted, so a second call is unauthenticated.
        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_returnsTheCallersProfile_andRequiresAToken() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email, "password123", "Test User")).andExpect(status().isCreated());
        String accessToken = accessToken(mockMvc.perform(login(email, "password123")).andReturn());

        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Test User"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.authProvider").value("LOCAL"))
                .andExpect(jsonPath("$.twoFactorEnabled").value(false))
                // Nothing secret leaves the server through the profile.
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.twoFactorSecret").doesNotExist());

        mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void googleLogin_withValidIdToken_createsUserAndReturnsTokens() throws Exception {
        String email = uniqueEmail();
        when(googleIdTokenDecoder.decode("google-token")).thenReturn(googleJwt("sub-" + email, email, "Google User"));

        mockMvc.perform(googleLogin("google-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(cookie().exists(AuthController.REFRESH_COOKIE))
                .andExpect(cookie().httpOnly(AuthController.REFRESH_COOKIE, true));
    }

    @Test
    void googleLogin_withInvalidIdToken_returns401() throws Exception {
        when(googleIdTokenDecoder.decode("bad-token")).thenThrow(new BadJwtException("bad signature"));

        mockMvc.perform(googleLogin("bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_oauth_token"));
    }

    @Test
    void googleLogin_whenEmailRegisteredWithPassword_returns409() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email, "password123", "Local User")).andExpect(status().isCreated());
        when(googleIdTokenDecoder.decode("token-collision")).thenReturn(googleJwt("sub-collide", email, "Google User"));

        mockMvc.perform(googleLogin("token-collision"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("email_registered_with_password"));
    }

    @Test
    void passwordLogin_againstOAuthAccount_returns401() throws Exception {
        String email = uniqueEmail();
        when(googleIdTokenDecoder.decode("oauth-token")).thenReturn(googleJwt("sub-" + email, email, "Google User"));
        mockMvc.perform(googleLogin("oauth-token")).andExpect(status().isOk());

        mockMvc.perform(login(email, "any-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder register(
            String email, String password, String fullName) {
        String body = """
                {"email":"%s","password":"%s","fullName":"%s"}""".formatted(email, password, fullName);
        return post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String email, String password) {
        String body = """
                {"email":"%s","password":"%s"}""".formatted(email, password);
        return post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder googleLogin(String idToken) {
        String body = """
                {"idToken":"%s"}""".formatted(idToken);
        return post("/auth/oauth/google").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private Jwt googleJwt(String sub, String email, String name) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(sub)
                .claim("email", email)
                .claim("email_verified", true)
                .claim("name", name)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String refreshCookieValue(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).isNotNull();
        return header.split(";", 2)[0].split("=", 2)[1];
    }

    private Cookie refreshCookie(String value) {
        return new Cookie(AuthController.REFRESH_COOKIE, value);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
