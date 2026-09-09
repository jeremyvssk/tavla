// Verifies Google reCAPTCHA tokens on registration; gated by app.recaptcha.enabled.
package com.iloveshopping.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.iloveshopping.auth.exception.InvalidCaptchaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * When enabled, verifies the client's reCAPTCHA token against Google's siteverify endpoint.
 * When disabled (the default for dev/test, where no key is provisioned) {@link #verify} is a
 * no-op, so the real integration ships but registration still works without keys. Flip
 * {@code app.recaptcha.enabled=true} with a real {@code RECAPTCHA_SECRET_KEY} to activate it.
 */
@Service
public class CaptchaService {

    private final RestClient restClient;
    private final boolean enabled;
    private final String secretKey;
    private final String verifyUrl;
    private final String expectedHostname;

    public CaptchaService(RestClient.Builder restClientBuilder,
                          @Value("${app.recaptcha.enabled}") boolean enabled,
                          @Value("${app.recaptcha.secret-key}") String secretKey,
                          @Value("${app.recaptcha.verify-url}") String verifyUrl,
                          @Value("${app.recaptcha.expected-hostname}") String expectedHostname) {
        this.restClient = restClientBuilder.build();
        this.enabled = enabled;
        this.secretKey = secretKey;
        this.verifyUrl = verifyUrl;
        this.expectedHostname = expectedHostname;
    }

    /**
     * @throws InvalidCaptchaException if CAPTCHA is enabled and the token is missing or rejected.
     */
    public void verify(String token) {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(token)) {
            throw new InvalidCaptchaException();
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", token);

        SiteVerifyResponse response = restClient.post()
                .uri(verifyUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(SiteVerifyResponse.class);

        if (response == null || !response.success()) {
            throw new InvalidCaptchaException();
        }
        // "Solved" is not enough: a token farmed on a site the attacker controls also comes back
        // successful. Google reports where it was solved, and that is compared against configuration
        // — never against the request's own Host header, which the caller sets.
        if (StringUtils.hasText(expectedHostname) && !expectedHostname.equals(response.hostname())) {
            throw new InvalidCaptchaException();
        }
    }

    // Google returns extra fields (challenge_ts, error-codes); ignore them. The app's Jackson is
    // configured to fail on unknown properties, so this annotation is required here.
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SiteVerifyResponse(boolean success, String hostname) {
    }
}
