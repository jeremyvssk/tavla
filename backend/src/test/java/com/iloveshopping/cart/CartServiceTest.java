// Unit tests for the cart's item management and arithmetic: totals, sold-out and short lines, stock caps, the line limit.
package com.iloveshopping.cart;

import com.iloveshopping.cart.dto.CartLineResponse;
import com.iloveshopping.cart.dto.CartResponse;
import com.iloveshopping.cart.exception.CartNotFoundException;
import com.iloveshopping.cart.exception.NotEnoughStockException;
import com.iloveshopping.catalog.ProductService;
import com.iloveshopping.catalog.dto.ProductForCart;
import com.iloveshopping.catalog.search.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The repositories and the catalog are mocks, so these run in milliseconds and pin the rules
 * themselves. CartIT checks the same rules against the real database, including the race.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartServiceTest {

    @Mock
    CartRepository carts;
    @Mock
    CartItemRepository items;
    @Mock
    ProductService productService;
    @Mock
    SearchService searchService;

    private CartService service;
    private final UUID userId = UUID.randomUUID();
    private final UUID cartId = UUID.randomUUID();
    private final CartOwner owner = CartOwner.user(userId);
    private final Map<UUID, ProductForCart> catalog = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new CartService(carts, items, productService, searchService);
        Cart cart = mock(Cart.class);
        given(cart.getId()).willReturn(cartId);
        given(carts.findByUserId(userId)).willReturn(Optional.of(cart));
        given(productService.forCart(any())).willAnswer(inv -> {
            Map<UUID, ProductForCart> found = new HashMap<>();
            for (Object id : inv.getArgument(0, Collection.class)) {
                if (catalog.containsKey(id)) {
                    found.put((UUID) id, catalog.get(id));
                }
            }
            return found;
        });
    }

    private UUID product(String price, int stock, boolean active) {
        UUID id = UUID.randomUUID();
        catalog.put(id, new ProductForCart(id, "Product " + id.toString().substring(0, 4), new BigDecimal(price), stock, active,
                "Brand", null, null, null));
        return id;
    }

    private void inCart(Object... productAndQuantity) {
        List<CartItem> lines = new java.util.ArrayList<>();
        for (int i = 0; i < productAndQuantity.length; i += 2) {
            CartItem line = mock(CartItem.class);
            given(line.getProductId()).willReturn((UUID) productAndQuantity[i]);
            given(line.getQuantity()).willReturn((Integer) productAndQuantity[i + 1]);
            given(items.findById(new CartItem.Key(cartId, (UUID) productAndQuantity[i]))).willReturn(Optional.of(line));
            lines.add(line);
        }
        given(items.findByIdCartIdOrderByAddedAtAscIdProductIdAsc(cartId)).willReturn(lines);
    }

    // ---- totals ----

    @Test
    void theSubtotal_isEachPriceTimesItsQuantity_addedUp_exactly() {
        UUID board = product("24.00", 5, true);
        UUID clock = product("65.00", 3, true);
        UUID pieces = product("0.10", 50, true);
        inCart(board, 2, clock, 1, pieces, 3);

        CartResponse cart = service.get(owner);

        assertThat(cart.itemCount()).isEqualTo(6);
        // 48.00 + 65.00 + 0.30, in BigDecimal: no 0.1 + 0.2 drift
        assertThat(cart.subtotal()).isEqualByComparingTo("113.30");
        assertThat(cart.lines()).extracting(CartLineResponse::lineTotal)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("48.00"), new BigDecimal("65.00"), new BigDecimal("0.30"));
        assertThat(cart.lines()).extracting(CartLineResponse::state).containsOnly("ok");
    }

    @Test
    void aSoldOutLine_staysVisible_butCountsForNothing() {
        UUID board = product("24.00", 5, true);
        UUID gone = product("65.00", 0, true);
        inCart(board, 1, gone, 2);

        CartResponse cart = service.get(owner);

        CartLineResponse soldOut = cart.lines().get(1);
        assertThat(soldOut.state()).isEqualTo("sold_out");
        assertThat(soldOut.countedQuantity()).isZero();
        assertThat(soldOut.lineTotal()).isEqualByComparingTo("0");
        assertThat(cart.subtotal()).isEqualByComparingTo("24.00");
        assertThat(cart.itemCount()).isEqualTo(1);
    }

    @Test
    void aLineAboveTheStock_isCountedAtTheStock_andFlaggedShort() {
        UUID clock = product("65.00", 2, true);
        inCart(clock, 5);

        CartLineResponse line = service.get(owner).lines().get(0);

        assertThat(line.state()).isEqualTo("short");
        assertThat(line.quantity()).isEqualTo(5);
        assertThat(line.countedQuantity()).isEqualTo(2);
        assertThat(line.lineTotal()).isEqualByComparingTo("130.00");
        assertThat(line.maxQuantity()).isEqualTo(2);
    }

    @Test
    void aProductNoLongerSold_isUnavailable_andAProductDeletedFromTheCatalog_isLeftOut() {
        UUID retired = product("40.00", 9, false);
        UUID deleted = UUID.randomUUID();
        inCart(retired, 1, deleted, 1);

        CartResponse cart = service.get(owner);

        assertThat(cart.lines()).hasSize(1);
        assertThat(cart.lines().get(0).state()).isEqualTo("unavailable");
        assertThat(cart.subtotal()).isEqualByComparingTo("0");
    }

    @Test
    void noCart_isAnEmptyCart_andReadingNeverCreatesOne() {
        CartOwner guest = CartOwner.guest(null);
        CartResponse cart = service.get(guest);
        assertThat(cart.lines()).isEmpty();
        assertThat(cart.subtotal()).isEqualByComparingTo("0");
        verify(carts, never()).insertForGuest(any(), any());
    }

    // ---- adding ----

    @Test
    void adding_isCappedAtTheStock_orTheLineLimit_whicheverIsLower() {
        UUID few = product("10.00", 3, true);
        UUID many = product("10.00", 500, true);
        inCart();
        given(items.addUpTo(eq(cartId), any(), anyInt(), anyInt())).willReturn(1);

        service.add(owner, few, 1);
        service.add(owner, many, 1);

        verify(items).addUpTo(cartId, few, 1, 3);
        verify(items).addUpTo(cartId, many, 1, CartService.LINE_LIMIT);
    }

    @Test
    void addingPastTheStock_saysHowManyAreLeft() {
        UUID clock = product("65.00", 3, true);
        inCart(clock, 2);
        given(items.addUpTo(cartId, clock, 2, 3)).willReturn(0);

        assertThatThrownBy(() -> service.add(owner, clock, 2))
                .isInstanceOfSatisfying(NotEnoughStockException.class, e -> {
                    assertThat(e.getBody().error()).isEqualTo("not_enough_stock");
                    assertThat(e.getBody().maxQuantity()).isEqualTo(3);
                    assertThat(e.getBody().inCart()).isEqualTo(2);
                });
    }

    @Test
    void addingPastTheLineLimit_isItsOwnReason() {
        UUID clock = product("65.00", 40, true);
        inCart(clock, 10);
        given(items.addUpTo(cartId, clock, 1, CartService.LINE_LIMIT)).willReturn(0);

        assertThatThrownBy(() -> service.add(owner, clock, 1))
                .isInstanceOfSatisfying(NotEnoughStockException.class, e -> assertThat(e.getBody().error()).isEqualTo("line_limit"));
    }

    @Test
    void addingSomethingSoldOut_orNotSold_isRefusedBeforeTheCartIsTouched() {
        UUID gone = product("65.00", 0, true);
        UUID retired = product("65.00", 5, false);

        assertThatThrownBy(() -> service.add(owner, gone, 1))
                .isInstanceOfSatisfying(NotEnoughStockException.class, e -> assertThat(e.getBody().error()).isEqualTo("out_of_stock"));
        assertThatThrownBy(() -> service.add(owner, retired, 1))
                .isInstanceOfSatisfying(CartNotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("product_not_found"));
        assertThatThrownBy(() -> service.add(owner, UUID.randomUUID(), 1)).isInstanceOf(CartNotFoundException.class);
        verify(items, never()).addUpTo(any(), any(), anyInt(), anyInt());
    }

    // ---- changing and removing ----

    @Test
    void lowering_alwaysWorks_evenBelowAStockThatDropped() {
        UUID clock = product("65.00", 1, true);
        inCart(clock, 4);

        service.setQuantity(owner, clock, 2);

        verify(items).setQuantity(cartId, clock, 2);
    }

    @Test
    void raising_isCheckedAgainstTheStock() {
        UUID clock = product("65.00", 3, true);
        inCart(clock, 1);

        service.setQuantity(owner, clock, 3);
        verify(items).setQuantity(cartId, clock, 3);

        assertThatThrownBy(() -> service.setQuantity(owner, clock, 4)).isInstanceOf(NotEnoughStockException.class);
        verify(items, never()).setQuantity(cartId, clock, 4);
    }

    @Test
    void removingALineThatIsntThere_isNotAnError() {
        inCart();
        given(items.deleteLine(eq(cartId), any())).willReturn(0);

        CartResponse cart = service.remove(owner, UUID.randomUUID()).cart();

        assertThat(cart.lines()).isEmpty();
        verify(carts, never()).touch(cartId);
    }
}
