// Unit tests for CaptchaService against a mocked Google siteverify endpoint.
package com.iloveshopping.auth;

import com.iloveshopping.auth.exception.InvalidCaptchaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CaptchaServiceTest {

    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void disabled_acceptsAnyToken_withoutCallingGoogle() {
        CaptchaService service = new CaptchaService(builder, false, "secret", VERIFY_URL);

        service.verify(null);
        service.verify("whatever");

        server.verify(); // no HTTP calls were made
    }

    @Test
    void enabled_passesWhenGoogleReportsSuccess() {
        server.expect(requestTo(VERIFY_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));
        CaptchaService service = new CaptchaService(builder, true, "secret", VERIFY_URL);

        assertThatNoException().isThrownBy(() -> service.verify("good-token"));
        server.verify();
    }

    @Test
    void enabled_throwsWhenGoogleReportsFailure() {
        server.expect(requestTo(VERIFY_URL))
                .andRespond(withSuccess("{\"success\":false}", MediaType.APPLICATION_JSON));
        CaptchaService service = new CaptchaService(builder, true, "secret", VERIFY_URL);

        assertThatThrownBy(() -> service.verify("bad-token"))
                .isInstanceOf(InvalidCaptchaException.class);
        server.verify();
    }

    @Test
    void enabled_throwsWhenTokenBlank_withoutCallingGoogle() {
        CaptchaService service = new CaptchaService(builder, true, "secret", VERIFY_URL);

        assertThatThrownBy(() -> service.verify("   "))
                .isInstanceOf(InvalidCaptchaException.class);
        server.verify(); // short-circuits before any HTTP call
    }
}
