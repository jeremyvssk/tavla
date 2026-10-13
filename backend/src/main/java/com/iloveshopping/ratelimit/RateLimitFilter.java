// Per-IP token buckets, applied before authentication: a tight one per guessable or expensive endpoint, a wide one for the rest.
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
 * The whole per-IP policy on one screen. Each rule is a token bucket (RateLimiter): {@code limit} is the burst,
 * and the bucket earns that many back evenly over {@code window}, so "10 per minute" is 10 at once, then
 * one every 6 seconds. Login and forgot-password are also limited per account,
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
            new Rule("GET", "/search/suggestions", 120, Duration.ofMinutes(1)),
            // Each add without a cookie makes a guest cart row; a person adds a few items a minute.
            new Rule("POST", "/cart/items", 60, Duration.ofMinutes(1)),
            // Every order holds its stock for half an hour, so a script placing orders it never pays
            // could empty the shelves. Ten in ten minutes is far above a person checking out.
            new Rule("POST", "/orders", 10, Duration.ofMinutes(10)),
            // Each message is an email in the shop's inbox; a person writes a few, a script thousands.
            new Rule("POST", "/support/messages", 5, Duration.ofMinutes(15)));

    /**
     * Every other request: a burst of 300, then 20 a second. A product page is about ten API calls (images
     * come from nginx, not here), so a person never meets it, and several shoppers behind one office or
     * phone-network address still fit. A script hammering any endpoint is held to 20 a second.
     */
    private static final Rule GENERAL = new Rule("*", "*", 300, Duration.ofSeconds(15));

    // Stripe's events are signed and retried by Stripe; refusing one only delays a payment.
    private static final String WEBHOOK = "/payments/webhook";

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
        Rule rule = GENERAL;
        for (Rule r : RULES) {
            if (r.method().equals(request.getMethod()) && r.path().equals(request.getRequestURI())) {
                rule = r;
                break;
            }
        }
        if (rule == GENERAL && WEBHOOK.equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        String key = "ip:" + (rule == GENERAL ? "all" : rule.method() + rule.path()) + ":" + request.getRemoteAddr();
        RateLimiter.Result result = rateLimiter.hit(key, rule.limit(), rule.window());
        if (!result.allowed()) {
            // Written here rather than thrown: a filter runs outside the controller advice.
            response.setStatus(429);
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(result.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"too_many_requests\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
