// Stateless Spring Security wiring: JWT filter, public auth endpoints, JSON 401/403 responses.
package com.iloveshopping.config;

import com.iloveshopping.auth.JwtAuthenticationFilter;
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
                        // Reviews are written by any signed-in user; ReviewService checks ownership on delete.
                        .requestMatchers(HttpMethod.POST, "/products/*/reviews").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/products/*/reviews/*").authenticated()
                        // Every other catalog write is admin-only. Written as one path rule, so a new
                        // write endpoint under these paths is protected before anyone remembers to.
                        .requestMatchers("/products", "/products/**", "/categories", "/categories/**",
                                "/brands", "/brands/**")
                        .hasRole("ADMIN")
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
        return (request, response, accessDeniedException) -> writeError(response, HttpServletResponse.SC_FORBIDDEN, "forbidden");
    }

    private void writeError(HttpServletResponse response, int status, String error) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + error + "\"}");
    }
}
