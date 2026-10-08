// Cart endpoints for guests and signed-in users; a guest's cart is found by an HttpOnly cookie.
package com.iloveshopping.cart;

import com.iloveshopping.auth.AuthPrincipal;
import com.iloveshopping.cart.dto.AddToCartRequest;
import com.iloveshopping.cart.dto.CartResponse;
import com.iloveshopping.cart.dto.UpdateQuantityRequest;
import com.iloveshopping.catalog.dto.ProductSummary;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Public: a guest has no token, so the cookie is their only key. The cookie is HttpOnly, so a script
 * injected into the page can't read it, and SameSite=Strict, so another site can't make the browser
 * send it; that is what stands in for a CSRF token on these cookie-authenticated writes. Every guest
 * write sends it again, so it expires a set time after the cart was last touched, like the cart.
 */
@RestController
@RequestMapping("/cart")
public class CartController {

    static final String GUEST_COOKIE = CartOwner.GUEST_COOKIE;

    private final CartService cartService;
    private final boolean cookieSecure;
    private final Duration guestTtl;

    public CartController(CartService cartService, @Value("${app.cookie.secure}") boolean cookieSecure,
                          @Value("${app.cart.guest-ttl}") String guestTtl) {
        this.cartService = cartService;
        this.cookieSecure = cookieSecure;
        this.guestTtl = Duration.parse(guestTtl);
    }

    @GetMapping
    public CartResponse get(@AuthenticationPrincipal AuthPrincipal principal, HttpServletRequest request) {
        return cartService.get(CartOwner.of(principal, request));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> add(@AuthenticationPrincipal AuthPrincipal principal, HttpServletRequest request,
                                            @Valid @RequestBody AddToCartRequest body) {
        return respond(cartService.add(CartOwner.of(principal, request), body.productId(), body.quantity()));
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<CartResponse> setQuantity(@AuthenticationPrincipal AuthPrincipal principal,
                                                    HttpServletRequest request, @PathVariable UUID productId,
                                                    @Valid @RequestBody UpdateQuantityRequest body) {
        return respond(cartService.setQuantity(CartOwner.of(principal, request), productId, body.quantity()));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<CartResponse> remove(@AuthenticationPrincipal AuthPrincipal principal,
                                               HttpServletRequest request, @PathVariable UUID productId) {
        return respond(cartService.remove(CartOwner.of(principal, request), productId));
    }

    @GetMapping("/recommendations")
    public List<ProductSummary> recommendations(@AuthenticationPrincipal AuthPrincipal principal,
                                                HttpServletRequest request) {
        return cartService.recommendations(CartOwner.of(principal, request));
    }

    /**
     * Called by the app right after every sign-in (SecurityConfig asks for a token): the guest cart this
     * browser built is merged into the account's, and the guest cookie is cleared, so signing out later
     * starts an empty guest cart instead of bringing the old one back.
     */
    @PostMapping("/merge")
    public ResponseEntity<CartResponse> merge(@AuthenticationPrincipal AuthPrincipal principal, HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, GUEST_COOKIE);
        CartResponse cart = cartService.mergeGuestCart(principal.userId(), cookie == null ? null : cookie.getValue());
        if (cookie == null) {
            return ResponseEntity.ok(cart);
        }
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, guestCookie("", Duration.ZERO).toString()).body(cart);
    }

    private ResponseEntity<CartResponse> respond(CartService.CartWrite write) {
        if (write.guestToken() == null) {
            return ResponseEntity.ok(write.cart());
        }
        ResponseCookie cookie = guestCookie(write.guestToken(), guestTtl);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(write.cart());
    }

    private ResponseCookie guestCookie(String value, Duration maxAge) {
        return ResponseCookie.from(GUEST_COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                // the whole site, not just /cart: placing and reading a guest's order finds them by it too
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
