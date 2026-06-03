// Integration tests for the auth endpoints against real Postgres + Redis (Testcontainers).
package com.iloveshopping.auth;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AuthControllerIT {

    // Singleton containers started once and shared; Testcontainers' Ryuk reaps them at JVM exit.
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        postgres.start();
        redis.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.url",
                () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        registry.add("app.jwt.secret", () -> "integration-test-secret-that-is-long-enough-hs256");
        // Mail is unused in these tests; a host just satisfies the auto-configuration.
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "1025");
    }

    @Autowired
    WebApplicationContext context;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

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
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isUnauthorized());
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
