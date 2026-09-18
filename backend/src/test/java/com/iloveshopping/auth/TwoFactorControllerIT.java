// Integration tests for the full 2FA flow (setup, enable, challenge login, backup codes, disable).
package com.iloveshopping.auth;

import com.iloveshopping.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TwoFactorControllerIT extends AbstractIntegrationTest {

    private static final String PW = "password123";

    private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();

    @Test
    void fullTotpFlow_setupEnableChallengeLoginAndDisable() throws Exception {
        Enrolled user = enroll();

        // Login now returns a 2FA challenge, not tokens, and sets no refresh cookie.
        MvcResult challengeResult = mockMvc.perform(login(user.email()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorRequired").value(true))
                .andExpect(jsonPath("$.challenge").isNotEmpty())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(cookie().doesNotExist(AuthController.REFRESH_COOKIE))
                .andReturn();
        String challenge = JsonPath.read(challengeResult.getResponse().getContentAsString(), "$.challenge");

        // A wrong code is rejected but doesn't consume the challenge.
        mockMvc.perform(twoFactorLogin(challenge, wrongCode(user.secret())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_2fa_code"));

        // The correct TOTP completes the login with tokens + refresh cookie.
        mockMvc.perform(twoFactorLogin(challenge, currentCode(user.secret())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(cookie().exists(AuthController.REFRESH_COOKIE));

        // The now-consumed challenge can't be reused.
        mockMvc.perform(twoFactorLogin(challenge, currentCode(user.secret())))
                .andExpect(status().isUnauthorized());

        // Disabling 2FA requires the account password.
        mockMvc.perform(post("/auth/2fa/disable")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PW + "\"}"))
                .andExpect(status().isNoContent());

        // With 2FA off, login returns tokens directly again.
        mockMvc.perform(login(user.email()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void setup_withoutThePassword_isRejectedEvenWithAValidAccessToken() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email)).andExpect(status().isCreated());
        String access = accessToken(mockMvc.perform(login(email)).andReturn());

        // No body at all: the endpoint used to accept this and hand out a fresh secret.
        mockMvc.perform(post("/auth/2fa/setup")
                        .header(HttpHeaders.AUTHORIZATION, bearer(access)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/auth/2fa/setup")
                        .header(HttpHeaders.AUTHORIZATION, bearer(access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"not-the-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    void setup_onAnAccountThatAlreadyHasTwoFactor_isRefused() throws Exception {
        // Otherwise a stolen access token repoints the second factor at the attacker's device.
        Enrolled user = enroll();

        mockMvc.perform(post("/auth/2fa/setup")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PW + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("two_factor_already_enabled"));
    }

    @Test
    void backupCode_completesLoginOnce_thenIsRejected() throws Exception {
        Enrolled user = enroll();
        String backupCode = user.backupCodes().get(0);

        // A backup code completes the second login step.
        mockMvc.perform(twoFactorLogin(challengeFor(user.email()), backupCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // Reusing it (against a fresh challenge) fails — backup codes are single-use.
        mockMvc.perform(twoFactorLogin(challengeFor(user.email()), backupCode))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_2fa_code"));
    }

    private Enrolled enroll() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email)).andExpect(status().isCreated());
        String access = accessToken(mockMvc.perform(login(email)).andReturn());

        MvcResult setup = mockMvc.perform(post("/auth/2fa/setup")
                        .header(HttpHeaders.AUTHORIZATION, bearer(access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andExpect(jsonPath("$.otpauthUri", startsWith("otpauth://totp/")))
                .andReturn();
        String secret = JsonPath.read(setup.getResponse().getContentAsString(), "$.secret");

        MvcResult enabled = mockMvc.perform(post("/auth/2fa/enable")
                        .header(HttpHeaders.AUTHORIZATION, bearer(access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + currentCode(secret) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backupCodes", hasSize(8)))
                .andReturn();
        List<String> backupCodes = JsonPath.read(enabled.getResponse().getContentAsString(), "$.backupCodes");

        return new Enrolled(email, secret, access, backupCodes);
    }

    private String challengeFor(String email) throws Exception {
        MvcResult result = mockMvc.perform(login(email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorRequired").value(true))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.challenge");
    }

    private MockHttpServletRequestBuilder register(String email) {
        String body = """
                {"email":"%s","password":"%s","fullName":"2FA User"}""".formatted(email, PW);
        return post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder login(String email) {
        String body = """
                {"email":"%s","password":"%s"}""".formatted(email, PW);
        return post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder twoFactorLogin(String challenge, String code) {
        String body = """
                {"challenge":"%s","code":"%s"}""".formatted(challenge, code);
        return post("/auth/2fa/login").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private String currentCode(String secret) throws Exception {
        return codeGenerator.generate(secret, Math.floorDiv(timeProvider.getTime(), 30));
    }

    private String wrongCode(String secret) throws Exception {
        long counter = Math.floorDiv(timeProvider.getTime(), 30);
        Set<String> valid = Set.of(
                codeGenerator.generate(secret, counter - 1),
                codeGenerator.generate(secret, counter),
                codeGenerator.generate(secret, counter + 1));
        for (int i = 0; i < 1_000_000; i++) {
            String candidate = String.format("%06d", i);
            if (!valid.contains(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("no non-matching code found");
    }

    private record Enrolled(String email, String secret, String accessToken, List<String> backupCodes) {
    }
}
