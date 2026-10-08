// Whose cart a request is about: the signed-in user, or a guest and the token from their cart cookie.
package com.iloveshopping.cart;

import com.iloveshopping.auth.AuthPrincipal;
import com.iloveshopping.cart.exception.RejectedTokenException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.WebUtils;

import java.util.UUID;

/** For a guest, {@code guestToken} is the raw cookie value, or null when the browser has none yet. */
public record CartOwner(UUID userId, String guestToken) {

    public static final String GUEST_COOKIE = "cart_token";

    static CartOwner user(UUID userId) {
        return new CartOwner(userId, null);
    }

    static CartOwner guest(String guestToken) {
        return new CartOwner(null, guestToken);
    }

    /**
     * The account when the token checked out, else the guest cookie. A token that was sent but rejected
     * (expired, say) is a 401, not a quiet fall-back to the guest cart, which would show a signed-in
     * shopper an empty one. Orders find their owner the same way.
     */
    public static CartOwner of(AuthPrincipal principal, HttpServletRequest request) {
        if (principal != null) {
            return user(principal.userId());
        }
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            throw new RejectedTokenException();
        }
        Cookie cookie = WebUtils.getCookie(request, GUEST_COOKIE);
        return guest(cookie == null ? null : cookie.getValue());
    }

    public boolean isGuest() {
        return userId == null;
    }
}
