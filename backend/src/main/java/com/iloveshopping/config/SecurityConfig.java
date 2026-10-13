// Stateless Spring Security wiring: JWT filter, public auth endpoints, JSON 401/403 responses.
package com.iloveshopping.config;

import com.iloveshopping.auth.JwtAuthenticationFilter;
import com.iloveshopping.auth.StaffArea;
import com.iloveshopping.auth.exception.TwoFactorRequiredException;
import com.iloveshopping.ratelimit.RateLimitFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, RateLimitFilter rateLimitFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Stateless API: auth state is the JWT + Redis, never an HTTP session.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // No CSRF tokens: state-changing requests carry a Bearer header (not auto-sent),
                // and the refresh cookie is SameSite=Strict + Path=/auth.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/auth/register", "/auth/login", "/auth/refresh", "/auth/oauth/google",
                                "/auth/forgot-password", "/auth/reset-password", "/auth/2fa/login")
                        .permitAll()
                        // Catalog: anyone may read. Order matters, first match wins.
                        .requestMatchers(HttpMethod.GET,
                                "/products", "/products/**", "/categories", "/categories/**",
                                "/brands", "/brands/**", "/search/**")
                        .permitAll()
                        // The contact form is for everyone; CAPTCHA, a per-address limit and length caps guard it.
                        .requestMatchers(HttpMethod.POST, "/support/messages").permitAll()
                        // The checkout lists the delivery options before anyone signs in.
                        .requestMatchers(HttpMethod.GET, "/delivery-options").permitAll()
                        // Merging a guest cart into an account needs the account.
                        .requestMatchers(HttpMethod.POST, "/cart/merge").authenticated()
                        // The cart serves guests too: CartController picks the account's cart when a token is
                        // present and the guest cookie's cart when not.
                        .requestMatchers("/cart", "/cart/**").permitAll()
                        // Guests place, read, pay and cancel their own orders, found by the same cart cookie; OrderService
                        // answers 404 for anyone else's. The account's order list (GET /orders, matched by nothing
                        // here, so it falls to anyRequest) and its last delivery details stay behind a login.
                        .requestMatchers(HttpMethod.GET, "/orders/last-delivery").authenticated()
                        .requestMatchers(HttpMethod.POST, "/orders", "/orders/*/payment", "/orders/*/cancel").permitAll()
                        .requestMatchers(HttpMethod.GET, "/orders/*").permitAll()
                        // Stripe has no token: the webhook proves itself with its Stripe-Signature header.
                        .requestMatchers(HttpMethod.POST, "/payments/webhook").permitAll()
                        // Reviews are written, voted on and reported by any signed-in user; ReviewService checks the purchase and ownership.
                        .requestMatchers(HttpMethod.POST, "/products/*/reviews").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/products/*/reviews/*").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/products/*/reviews/*/vote").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/products/*/reviews/*/vote").authenticated()
                        .requestMatchers(HttpMethod.POST, "/products/*/reviews/*/report").authenticated()
                        // Every other catalog write is for the staff who run the catalog. Written as one path rule,
                        // so a new write endpoint under these paths is protected before anyone remembers to.
                        .requestMatchers("/products", "/products/**", "/categories", "/categories/**",
                                "/brands", "/brands/**")
                        .access(StaffArea.CATALOG.check())
                        // The admin's API, one area per row (StaffArea has who may use each, and every one needs a
                        // second factor). The last row closes any staff path that has no row of its own yet.
                        .requestMatchers("/staff/overview").access(StaffArea.OVERVIEW.check())
                        .requestMatchers("/staff/orders", "/staff/orders/**").access(StaffArea.ORDERS.check())
                        .requestMatchers("/staff/reviews", "/staff/reviews/**").access(StaffArea.REVIEWS.check())
                        .requestMatchers("/staff/products", "/staff/products/**", "/staff/categories", "/staff/categories/**",
                                "/staff/imports", "/staff/imports/**")
                        .access(StaffArea.CATALOG.check())
                        .requestMatchers("/staff/delivery", "/staff/delivery/**").access(StaffArea.DELIVERY.check())
                        .requestMatchers("/staff/users", "/staff/users/**").access(StaffArea.USERS.check())
                        .requestMatchers("/staff/audit", "/staff/audit/**").access(StaffArea.AUDIT.check())
                        .requestMatchers("/staff", "/staff/**").denyAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(forbiddenHandler()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Over-limit requests are turned away before any token parsing or password hashing.
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized");
    }

    private AccessDeniedHandler forbiddenHandler() {
        return (request, response, accessDeniedException) -> writeError(response, HttpServletResponse.SC_FORBIDDEN,
                accessDeniedException instanceof TwoFactorRequiredException ? "two_factor_required" : "forbidden");
    }

    private void writeError(HttpServletResponse response, int status, String error) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + error + "\"}");
    }
}
