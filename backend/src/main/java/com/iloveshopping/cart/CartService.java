// The cart: read, add, change a quantity, remove, recommendations, the merge at sign-in, and the hand-over to an order.
package com.iloveshopping.cart;

import com.iloveshopping.auth.OpaqueTokens;
import com.iloveshopping.cart.dto.CartLineResponse;
import com.iloveshopping.cart.dto.CartResponse;
import com.iloveshopping.cart.exception.CartNotFoundException;
import com.iloveshopping.cart.exception.NotEnoughStockException;
import com.iloveshopping.catalog.ProductService;
import com.iloveshopping.catalog.dto.ProductForCart;
import com.iloveshopping.catalog.dto.ProductSummary;
import com.iloveshopping.catalog.search.SearchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Prices and stock are read from the catalog on every read and every write; the browser only ever
 * sends a product id and a quantity. Stock is checked here so the shopper hears about it early, but
 * nothing is reserved: an item in a cart can still sell out. Reserving stock is the order's job.
 *
 * <p>A signed-in request always uses the account's cart, even when the browser still has a guest
 * cookie. The app calls {@link #mergeGuestCart} right after sign-in, which empties that cookie's cart
 * into the account's.
 */
@Service
public class CartService {

    /** One line never goes above this, whatever the stock says: a shop, not a wholesaler. */
    public static final int LINE_LIMIT = 10;
    static final int RECOMMENDATION_LIMIT = 8;

    private static final CartResponse EMPTY = new CartResponse(List.of(), 0, BigDecimal.ZERO);

    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProductService productService;
    private final SearchService searchService;

    public CartService(CartRepository carts, CartItemRepository items, ProductService productService,
                       SearchService searchService) {
        this.carts = carts;
        this.items = items;
        this.productService = productService;
        this.searchService = searchService;
    }

    /** The result of a write, and the guest token the browser should hold afterwards (null for a user). */
    public record CartWrite(CartResponse cart, String guestToken) {
    }

    /** Reading never creates a cart: a guest without a cookie simply has an empty one. */
    @Transactional(readOnly = true)
    public CartResponse get(CartOwner owner) {
        return find(owner).map(cart -> view(cart.getId())).orElse(EMPTY);
    }

    @Transactional
    public CartWrite add(CartOwner owner, UUID productId, int quantity) {
        ProductForCart product = productService.forCart(List.of(productId)).get(productId);
        if (product == null || !product.active()) {
            throw new CartNotFoundException("product_not_found");
        }
        if (product.stockQuantity() <= 0) {
            throw conflict(product.id(), 0, 0);
        }
        Owned cart = findOrCreate(owner);
        int max = Math.min(product.stockQuantity(), LINE_LIMIT);
        if (items.addUpTo(cart.id(), productId, quantity, max) == 0) {
            // rolls back, so a cart created just now for this add disappears with it
            throw conflict(productId, product.stockQuantity(), quantityInCart(cart.id(), productId));
        }
        carts.touch(cart.id());
        return new CartWrite(view(cart.id()), cart.guestToken());
    }

    /** Lowering always works, even below a stock that has dropped; raising is checked against the stock. */
    @Transactional
    public CartWrite setQuantity(CartOwner owner, UUID productId, int quantity) {
        Cart cart = find(owner).orElseThrow(() -> new CartNotFoundException("cart_item_not_found"));
        CartItem line = items.findById(new CartItem.Key(cart.getId(), productId))
                .orElseThrow(() -> new CartNotFoundException("cart_item_not_found"));
        if (quantity > line.getQuantity()) {
            ProductForCart product = productService.forCart(List.of(productId)).get(productId);
            int stock = product == null || !product.active() ? 0 : product.stockQuantity();
            if (quantity > Math.min(stock, LINE_LIMIT)) {
                throw conflict(productId, stock, line.getQuantity());
            }
        }
        items.setQuantity(cart.getId(), productId, quantity);
        carts.touch(cart.getId());
        return new CartWrite(view(cart.getId()), owner.guestToken());
    }

    /** Removing a line that isn't there is not an error: the cart already looks the way the shopper wants. */
    @Transactional
    public CartWrite remove(CartOwner owner, UUID productId) {
        Optional<Cart> cart = find(owner);
        if (cart.isEmpty()) {
            return new CartWrite(EMPTY, null);
        }
        if (items.deleteLine(cart.get().getId(), productId) > 0) {
            carts.touch(cart.get().getId());
        }
        return new CartWrite(view(cart.get().getId()), owner.guestToken());
    }

    /**
     * Your rule from step 2: where the guest cart and the account's cart both hold a product, the
     * larger quantity wins, not the sum, so signing in never doubles a line behind the shopper's back.
     * Guest-only lines move over as they are; stock is flagged on the next read like any other line.
     * The guest cart is deleted afterwards. Without a guest cookie this is just a read.
     */
    @Transactional
    public CartResponse mergeGuestCart(UUID userId, String guestToken) {
        Optional<Cart> guest = guestToken == null ? Optional.empty()
                : carts.findByGuestTokenHash(OpaqueTokens.sha256(guestToken));
        if (guest.isPresent()) {
            Owned account = findOrCreate(CartOwner.user(userId));
            items.mergeInto(guest.get().getId(), account.id());
            carts.deleteById(guest.get().getId());
            carts.touch(account.id());
        }
        return get(CartOwner.user(userId));
    }

    /** What an order is made from: the cart's lines in the order they were added. */
    public record OrderableCart(UUID cartId, List<OrderableLine> lines) {
    }

    public record OrderableLine(UUID productId, int quantity) {
    }

    /**
     * The cart row is locked until the order's transaction ends, so a double-clicked Pay places one
     * order: the second request waits, then finds the lines already gone.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<OrderableCart> lockForOrder(CartOwner owner) {
        return find(owner).flatMap(cart -> carts.lockById(cart.getId())).map(cart -> new OrderableCart(cart.getId(),
                items.findByIdCartIdOrderByAddedAtAscIdProductIdAsc(cart.getId()).stream()
                        .map(line -> new OrderableLine(line.getProductId(), line.getQuantity())).toList()));
    }

    /** The lines an order just took; sold-out lines it left behind stay in the cart. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void removeLines(UUID cartId, Collection<UUID> productIds) {
        items.deleteLines(cartId, productIds);
        carts.touch(cartId);
    }

    /**
     * Puts an order's lines back, when the order was withdrawn or its hold ran out. A line already in
     * the cart is raised to the order's quantity if lower, never lowered. A guest whose cart has expired
     * since gets nothing back: no browser can name that cart any more.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreLines(UUID userId, String guestTokenHash, List<OrderableLine> lines) {
        Optional<UUID> cartId = userId != null ? Optional.of(findOrCreate(CartOwner.user(userId)).id())
                : carts.findByGuestTokenHash(guestTokenHash).map(Cart::getId);
        cartId.ifPresent(id -> {
            lines.forEach(line -> items.restoreLine(id, line.productId(), Math.min(line.quantity(), LINE_LIMIT)));
            carts.touch(id);
        });
    }

    @Transactional(readOnly = true)
    public List<ProductSummary> recommendations(CartOwner owner) {
        List<UUID> productIds = find(owner)
                .map(cart -> items.findByIdCartIdOrderByAddedAtAscIdProductIdAsc(cart.getId()).stream()
                        .map(CartItem::getProductId).toList())
                .orElse(List.of());
        return searchService.complements(productIds, RECOMMENDATION_LIMIT);
    }

    private Optional<Cart> find(CartOwner owner) {
        if (!owner.isGuest()) {
            return carts.findByUserId(owner.userId());
        }
        return owner.guestToken() == null ? Optional.empty()
                : carts.findByGuestTokenHash(OpaqueTokens.sha256(owner.guestToken()));
    }

    private record Owned(UUID id, String guestToken) {
    }

    /**
     * A guest whose cookie names no cart (never had one, or it expired) gets a fresh token from the
     * server. Reusing the cookie's value instead would let a client pick its own, guessable, token.
     */
    private Owned findOrCreate(CartOwner owner) {
        Optional<Cart> existing = find(owner);
        if (existing.isPresent()) {
            return new Owned(existing.get().getId(), owner.guestToken());
        }
        if (!owner.isGuest()) {
            carts.insertForUser(UUID.randomUUID(), owner.userId());
            return new Owned(carts.findByUserId(owner.userId()).orElseThrow().getId(), null);
        }
        String token = OpaqueTokens.generate();
        UUID id = UUID.randomUUID();
        carts.insertForGuest(id, OpaqueTokens.sha256(token));
        return new Owned(id, token);
    }

    private int quantityInCart(UUID cartId, UUID productId) {
        return items.findById(new CartItem.Key(cartId, productId)).map(CartItem::getQuantity).orElse(0);
    }

    private static NotEnoughStockException conflict(UUID productId, int stock, int inCart) {
        String code = stock <= 0 ? "out_of_stock" : stock < LINE_LIMIT ? "not_enough_stock" : "line_limit";
        return new NotEnoughStockException(code, productId, Math.max(0, Math.min(stock, LINE_LIMIT)), inCart);
    }

    private CartResponse view(UUID cartId) {
        List<CartItem> lines = items.findByIdCartIdOrderByAddedAtAscIdProductIdAsc(cartId);
        Map<UUID, ProductForCart> products = productService.forCart(lines.stream().map(CartItem::getProductId).toList());
        List<CartLineResponse> out = new ArrayList<>();
        int count = 0;
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem line : lines) {
            ProductForCart p = products.get(line.getProductId());
            if (p == null) {
                continue;
            }
            int stock = p.active() ? Math.max(0, p.stockQuantity()) : 0;
            String state = !p.active() ? "unavailable"
                    : stock == 0 ? "sold_out"
                    : stock < line.getQuantity() ? "short" : "ok";
            int counted = Math.min(line.getQuantity(), stock);
            BigDecimal lineTotal = p.price().multiply(BigDecimal.valueOf(counted));
            out.add(new CartLineResponse(p.id(), p.name(), p.brandName(), p.options(), p.imageUrl(), p.imageFraming(),
                    p.price(), line.getQuantity(), counted, lineTotal, Math.min(stock, LINE_LIMIT), state));
            count += counted;
            subtotal = subtotal.add(lineTotal);
        }
        return new CartResponse(out, count, subtotal);
    }
}
