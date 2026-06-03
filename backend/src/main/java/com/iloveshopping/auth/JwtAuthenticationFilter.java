// Per-request filter that authenticates a Bearer access token and honours the JTI blocklist.
package com.iloveshopping.auth;

import com.iloveshopping.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Reads {@code Authorization: Bearer <jwt>}, verifies it, and populates the security context.
 * A bad, expired, or blocklisted token simply leaves the request unauthenticated — the
 * security chain then returns 401 for protected routes. Tokens are never looked up in the DB;
 * the only stateful check is the Redis blocklist peek for revocation.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final TokenStoreService tokenStore;

    public JwtAuthenticationFilter(JwtService jwtService, TokenStoreService tokenStore) {
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(header.substring(BEARER_PREFIX.length()));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            Claims claims = jwtService.parse(token).getPayload();
            if (tokenStore.isAccessTokenBlocklisted(claims.getId())) {
                return;
            }
            Role role = Role.valueOf(claims.get("role", String.class));
            AuthPrincipal principal = new AuthPrincipal(
                    UUID.fromString(claims.getSubject()), role,
                    claims.getId(), claims.getExpiration().toInstant());
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException e) {
            // Invalid signature/expiry/claims: leave the request anonymous.
            SecurityContextHolder.clearContext();
        }
    }
}
