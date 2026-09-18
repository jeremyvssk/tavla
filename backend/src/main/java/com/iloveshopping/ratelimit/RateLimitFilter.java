// Per-IP request limits on the endpoints that are guessable or expensive, applied before authentication.
package com.iloveshopping.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * The whole per-IP policy on one screen. Login and forgot-password are also limited per account,
 * in AccountThrottle, because an IP limit alone misses a guesser spread over many addresses.
 *
 * <p>{@code getRemoteAddr()} is the real client only because Tomcat's RemoteIpValve replaces it
 * with nginx's X-Real-IP (server.forward-headers-strategy in application.yml). nginx overwrites
 * that header, so a client can't choose its own address through the proxy.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String method, String path, int limit, Duration window) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("POST", "/auth/login", 10, Duration.ofMinutes(1)),
            new Rule("POST", "/auth/2fa/login", 10, Duration.ofMinutes(1)),
            new Rule("POST", "/auth/oauth/google", 10, Duration.ofMinutes(1)),
            new Rule("POST", "/auth/register", 10, Duration.ofHours(1)),
            new Rule("POST", "/auth/forgot-password", 5, Duration.ofMinutes(15)),
            new Rule("POST", "/auth/reset-password", 10, Duration.ofMinutes(15)),
            // A search is six queries. The limits are well above a person clicking facets or
            // typing into a debounced search box, and well below a script looping on the endpoint.
            new Rule("GET", "/products", 120, Duration.ofMinutes(1)),
            new Rule("GET", "/search/suggestions", 120, Duration.ofMinutes(1)));

    private final RateLimiter rateLimiter;
    private final boolean enabled;

    public RateLimitFilter(RateLimiter rateLimiter, @Value("${app.rate-limit.enabled}") boolean enabled) {
        this.rateLimiter = rateLimiter;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        for (Rule rule : RULES) {
            if (rule.method().equals(request.getMethod()) && rule.path().equals(request.getRequestURI())) {
                String key = "ip:" + rule.method() + rule.path() + ":" + request.getRemoteAddr();
                RateLimiter.Result result = rateLimiter.hit(key, rule.limit(), rule.window());
                if (!result.allowed()) {
                    // Written here rather than thrown: a filter runs outside the controller advice.
                    response.setStatus(429);
                    response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(result.retryAfterSeconds()));
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"error\":\"too_many_requests\"}");
                    return;
                }
                break;
            }
        }
        filterChain.doFilter(request, response);
    }
}
