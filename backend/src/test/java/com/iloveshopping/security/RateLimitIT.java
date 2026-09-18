// Integration tests for rate limiting: per-IP limits, the two per-account login layers, and the 429 shape.
package com.iloveshopping.security;

import com.iloveshopping.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Counters persist in the shared Redis for their whole window, so every test uses fresh random
 * client addresses and email addresses rather than relying on cleanup between tests.
 */
@TestPropertySource(properties = "app.rate-limit.enabled=true")
class RateLimitIT extends AbstractIntegrationTest {

    @Test
    void login_perIpLimit_returns429WithRetryAfter_andOtherAddressesAreUnaffected() throws Exception {
        String ip = randomIp();
        // Ten different accounts, so only the per-IP rule is in play.
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(login(uniqueEmail(), "guess", ip)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login(uniqueEmail(), "guess", ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("too_many_requests"));

        mockMvc.perform(login(uniqueEmail(), "guess", randomIp())).andExpect(status().isUnauthorized());
    }

    @Test
    void login_oneAddressGuessingOneAccount_isStopped_butTheOwnerElsewhereCanStillTry() throws Exception {
        String email = registered();
        String attacker = randomIp();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(email, "guess", attacker)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login(email, "guess", attacker)).andExpect(status().isTooManyRequests());

        mockMvc.perform(login(email, "password123", randomIp())).andExpect(status().isOk());
    }

    @Test
    void login_guessesSpreadOverManyAddresses_hitTheAccountLimit() throws Exception {
        String email = uniqueEmail();
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(login(email, "guess", randomIp())).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login(email, "guess", randomIp())).andExpect(status().isTooManyRequests());
    }

    @Test
    void login_aBlockedAddress_stopsAddingToTheAccountCounter() throws Exception {
        // One address hammering past its pair limit must not be able to lock the account for everyone.
        String email = registered();
        String attacker = randomIp();
        for (int i = 0; i < 9; i++) {
            mockMvc.perform(login(email, "guess", attacker));
        }
        for (int i = 0; i < 14; i++) {
            mockMvc.perform(login(email, "guess", randomIp())).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login(email, "password123", randomIp())).andExpect(status().isOk());
    }

    @Test
    void login_correctPassword_resetsTheCounters() throws Exception {
        String email = registered();
        String ip = randomIp();
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(login(email, "typo", ip)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login(email, "password123", ip)).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(login(email, "typo", ip)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void forgotPassword_perIpLimit() throws Exception {
        String ip = randomIp();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(forgot(uniqueEmail(), ip)).andExpect(status().isOk());
        }
        mockMvc.perform(forgot(uniqueEmail(), ip)).andExpect(status().isTooManyRequests());
    }

    @Test
    void suggestions_perIpLimit() throws Exception {
        String ip = randomIp();
        for (int i = 0; i < 120; i++) {
            mockMvc.perform(from(get("/search/suggestions").param("q", "wal"), ip)).andExpect(status().isOk());
        }
        mockMvc.perform(from(get("/search/suggestions").param("q", "wal"), ip))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void unlimitedEndpoints_areNotCounted() throws Exception {
        String ip = randomIp();
        for (int i = 0; i < 130; i++) {
            mockMvc.perform(from(get("/categories"), ip)).andExpect(status().isOk());
        }
    }

    private String registered() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(from(post("/auth/register"), randomIp()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"Rate Tester"}""".formatted(email)))
                .andExpect(status().isCreated());
        return email;
    }

    private static MockHttpServletRequestBuilder login(String email, String password, String ip) {
        return from(post("/auth/login"), ip).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}""".formatted(email, password));
    }

    private static MockHttpServletRequestBuilder forgot(String email, String ip) {
        return from(post("/auth/forgot-password"), ip).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s"}""".formatted(email));
    }

    private static MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder request, String ip) {
        return request.with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
    }

    private static String randomIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "10." + r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(1, 255);
    }

    private static String uniqueEmail() {
        return "rate-" + UUID.randomUUID() + "@example.com";
    }
}
