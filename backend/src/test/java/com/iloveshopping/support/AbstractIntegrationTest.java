// Shared base for *IT classes: one Postgres and one Redis container for the whole test JVM, plus MockMvc.
package com.iloveshopping.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Containers are started once in a static block and never stopped explicitly; they die with the
 * JVM. Every subclass reuses them, so a full `mvn verify` starts one pair instead of one per class.
 * Spring still caches one application context per distinct set of @MockitoBean overrides, but
 * those contexts all point at the same two containers.
 */
@SpringBootTest
// Every IT arrives from 127.0.0.1 and many register users, so limits are off by default. A
// @TestPropertySource rather than a dynamic property, because a subclass can override it: RateLimitIT does.
@TestPropertySource(properties = "app.rate-limit.enabled=false")
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    protected static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    protected static final Path imagesDir;

    static {
        postgres.start();
        redis.start();
        try {
            imagesDir = Files.createTempDirectory("ils-images-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.url",
                () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        registry.add("app.jwt.secret", () -> "integration-test-secret-that-is-long-enough-hs256");
        registry.add("app.oauth.google.client-id", () -> "test-client-id");
        // Mail is mocked or unused in tests; a host just satisfies the auto-configuration.
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "1025");
        registry.add("app.storage.images-dir", imagesDir::toString);
    }

    @Autowired
    protected WebApplicationContext context;

    protected MockMvc mockMvc;

    @BeforeEach
    void initMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
}
