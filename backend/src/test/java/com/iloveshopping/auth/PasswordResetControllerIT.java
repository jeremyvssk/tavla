// Integration tests for the forgot/reset-password flow against real Postgres + Redis (Testcontainers).
package com.iloveshopping.auth;

import com.iloveshopping.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordResetControllerIT extends AbstractIntegrationTest {

    // Mock the mail layer so tests never hit SMTP; we capture the emailed reset link instead.
    @MockitoBean
    EmailService emailService;

    @Test
    void forgotPassword_isAlways200_evenForUnknownEmail_andSendsNoMail() throws Exception {
        mockMvc.perform(forgot("nobody-" + UUID.randomUUID() + "@example.com"))
                .andExpect(status().isOk());
        // No user, so no email is sent (no enumeration via timing/side effects).
        org.mockito.Mockito.verifyNoInteractions(emailService);
    }

    @Test
    void resetPassword_changesPassword_revokesOldSessions_andEnforcesSingleUse() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email, "password123", "Reset User")).andExpect(status().isCreated());

        // Log in first so there is a live refresh token that the reset must revoke.
        String oldRefresh = refreshCookieValue(mockMvc.perform(login(email, "password123")).andReturn());

        // Request the reset and capture the raw token from the emailed link.
        mockMvc.perform(forgot(email)).andExpect(status().isOk());
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordReset(eq(email), url.capture());
        String token = url.getValue().substring(url.getValue().indexOf("token=") + "token=".length());

        // A wrong token is rejected.
        mockMvc.perform(reset("bogus-token", "newpassword123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_reset_token"));

        // The real token resets the password.
        mockMvc.perform(reset(token, "newpassword123")).andExpect(status().isOk());

        // Old session is dead: the pre-reset refresh token no longer works.
        mockMvc.perform(post("/auth/refresh").cookie(refreshCookie(oldRefresh)))
                .andExpect(status().isUnauthorized());

        // Old password no longer works; new password does.
        mockMvc.perform(login(email, "password123")).andExpect(status().isUnauthorized());
        mockMvc.perform(login(email, "newpassword123")).andExpect(status().isOk());

        // The token is single-use: replaying it fails.
        mockMvc.perform(reset(token, "anotherpass123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_reset_token"));
    }

    private MockHttpServletRequestBuilder register(String email, String password, String fullName) {
        String body = """
                {"email":"%s","password":"%s","fullName":"%s"}""".formatted(email, password, fullName);
        return post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder login(String email, String password) {
        String body = """
                {"email":"%s","password":"%s"}""".formatted(email, password);
        return post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder forgot(String email) {
        String body = """
                {"email":"%s"}""".formatted(email);
        return post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder reset(String token, String newPassword) {
        String body = """
                {"token":"%s","newPassword":"%s"}""".formatted(token, newPassword);
        return post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String refreshCookieValue(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).isNotNull();
        return header.split(";", 2)[0].split("=", 2)[1];
    }

    private Cookie refreshCookie(String value) {
        return new Cookie(AuthController.REFRESH_COOKIE, value);
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
