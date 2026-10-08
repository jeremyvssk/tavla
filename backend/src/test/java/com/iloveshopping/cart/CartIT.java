// Integration tests for the server cart: guest cookie, account carts, stock checks, the line limit, races, the sign-in merge and expiry.
package com.iloveshopping.cart;

import com.iloveshopping.support.CatalogTestSupport;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CartIT extends CatalogTestSupport {

    @Autowired
    GuestCartCleanup cleanup;

    private String admin;
    private int shelf;

    @BeforeEach
    void catalog() throws Exception {
        admin = adminToken();
        shelf = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'chess-clocks'", Integer.class);
    }

    private String product(String price, int stock) throws Exception {
        return product(price, stock, shelf);
    }

    private String product(String price, int stock, int category) throws Exception {
        String id = createProduct(admin, "Cart item " + suffix(), "for the cart", price, category, null);
        jdbc.update("UPDATE products SET stock_quantity = ? WHERE id = ?", stock, UUID.fromString(id));
        return id;
    }

    private static String add(String productId, int quantity) {
        return """
                {"productId":"%s","quantity":%d}""".formatted(productId, quantity);
    }

    private static MockHttpServletRequestBuilder withCookie(MockHttpServletRequestBuilder request, String token) {
        return token == null ? request : request.cookie(new Cookie(CartController.GUEST_COOKIE, token));
    }

    private MvcResult guestAdd(String token, String productId, int quantity) throws Exception {
        return mockMvc.perform(json(withCookie(post("/cart/items"), token), null, add(productId, quantity))).andReturn();
    }

    // ---- happy path ----

    @Test
    void aGuestAdd_setsAnHttpOnlyStrictCookie_andTheCartComesBackWithIt() throws Exception {
        String clock = product("65.00", 5);

        MvcResult first = mockMvc.perform(json(post("/cart/items"), null, add(clock, 2)))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly(CartController.GUEST_COOKIE, true))
                .andExpect(cookie().sameSite(CartController.GUEST_COOKIE, "Strict"))
                .andExpect(cookie().path(CartController.GUEST_COOKIE, "/"))
                .andExpect(jsonPath("$.lines[0].productId").value(clock))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(65.00))
                .andExpect(jsonPath("$.lines[0].lineTotal").value(130.00))
                .andExpect(jsonPath("$.lines[0].state").value("ok"))
                .andExpect(jsonPath("$.itemCount").value(2))
                .andExpect(jsonPath("$.subtotal").value(130.00))
                .andReturn();
        String token = first.getResponse().getCookie(CartController.GUEST_COOKIE).getValue();

        mockMvc.perform(withCookie(get("/cart"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0].quantity").value(2));
        // adding the same product again raises the line instead of making a second one
        mockMvc.perform(json(withCookie(post("/cart/items"), token), null, add(clock, 1)))
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0].quantity").value(3));
    }

    @Test
    void aGuestWithNoCookie_hasAnEmptyCart_andReadingItCreatesNothing() throws Exception {
        mockMvc.perform(get("/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(0)))
                .andExpect(jsonPath("$.subtotal").value(0))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void anAccountCart_followsTheUser_toANewSession() throws Exception {
        String clock = product("65.00", 5);
        String email = registerUser();

        mockMvc.perform(json(post("/cart/items"), tokenFor(email), add(clock, 1)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        // a second login is a second browser: same account, same cart
        mockMvc.perform(auth(get("/cart"), tokenFor(email)))
                .andExpect(jsonPath("$.lines[0].productId").value(clock));
    }

    // ---- validation ----

    @Test
    void badBodies_areRefused_withTheFieldNamed() throws Exception {
        String clock = product("65.00", 5);
        mockMvc.perform(json(post("/cart/items"), null, add(clock, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.quantity").exists());
        mockMvc.perform(json(post("/cart/items"), null, add(clock, 11)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.quantity").exists());
        mockMvc.perform(json(post("/cart/items"), null, """
                        {"quantity":1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.productId").exists());
        // the browser never sets a price: an extra field is refused, not ignored
        mockMvc.perform(json(post("/cart/items"), null, """
                        {"productId":"%s","quantity":1,"price":0.01}""".formatted(clock)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/cart/items"), null, add(UUID.randomUUID().toString(), 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("product_not_found"));
    }

    // ---- auth and ownership ----

    @Test
    void oneShoppersCart_isInvisibleToAnother() throws Exception {
        String clock = product("65.00", 5);
        mockMvc.perform(json(post("/cart/items"), customerToken(), add(clock, 1))).andExpect(status().isOk());
        String guest = guestAdd(null, clock, 1).getResponse().getCookie(CartController.GUEST_COOKIE).getValue();

        mockMvc.perform(auth(get("/cart"), customerToken())).andExpect(jsonPath("$.lines", hasSize(0)));
        mockMvc.perform(withCookie(get("/cart"), "not-" + guest)).andExpect(jsonPath("$.lines", hasSize(0)));
    }

    @Test
    void aCookieThatNamesNoCart_getsAFreshServerMadeToken_notItsOwn() throws Exception {
        String clock = product("65.00", 5);
        MvcResult result = guestAdd("chosen-by-the-client", clock, 1);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getCookie(CartController.GUEST_COOKIE).getValue())
                .isNotEqualTo("chosen-by-the-client")
                .hasSize(43);
    }

    @Test
    void aRejectedToken_is401_notAQuietFallBackToTheGuestCart() throws Exception {
        mockMvc.perform(auth(get("/cart"), "not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    // ---- stock and the line limit ----

    @Test
    void addingMoreThanTheStock_is409_withWhatIsLeft_andChangesNothing() throws Exception {
        String clock = product("65.00", 3);
        String token = guestAdd(null, clock, 2).getResponse().getCookie(CartController.GUEST_COOKIE).getValue();

        mockMvc.perform(json(withCookie(post("/cart/items"), token), null, add(clock, 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("not_enough_stock"))
                .andExpect(jsonPath("$.maxQuantity").value(3))
                .andExpect(jsonPath("$.inCart").value(2));
        mockMvc.perform(withCookie(get("/cart"), token)).andExpect(jsonPath("$.lines[0].quantity").value(2));
    }

    @Test
    void aSoldOutProduct_is409_andAFirstAddMakesNoCart() throws Exception {
        String gone = product("65.00", 0);
        mockMvc.perform(json(post("/cart/items"), null, add(gone, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("out_of_stock"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void aLineStopsAtTen_evenWithPlentyInStock() throws Exception {
        String clock = product("65.00", 50);
        String token = guestAdd(null, clock, 10).getResponse().getCookie(CartController.GUEST_COOKIE).getValue();
        mockMvc.perform(json(withCookie(post("/cart/items"), token), null, add(clock, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("line_limit"))
                .andExpect(jsonPath("$.maxQuantity").value(10));
    }

    @Test
    void stockThatDropsAfterAdding_showsOnTheNextRead_andLeavesTheTotal() throws Exception {
        String low = product("10.00", 5);
        String gone = product("20.00", 5);
        String retired = product("30.00", 5);
        String token = guestAdd(null, low, 4).getResponse().getCookie(CartController.GUEST_COOKIE).getValue();
        guestAdd(token, gone, 1);
        guestAdd(token, retired, 1);

        jdbc.update("UPDATE products SET stock_quantity = 1 WHERE id = ?", UUID.fromString(low));
        jdbc.update("UPDATE products SET stock_quantity = 0 WHERE id = ?", UUID.fromString(gone));
        jdbc.update("UPDATE products SET active = false WHERE id = ?", UUID.fromString(retired));

        mockMvc.perform(withCookie(get("/cart"), token))
                .andExpect(jsonPath("$.lines[0].state").value("short"))
                .andExpect(jsonPath("$.lines[0].quantity").value(4))
                .andExpect(jsonPath("$.lines[0].countedQuantity").value(1))
                .andExpect(jsonPath("$.lines[0].maxQuantity").value(1))
                .andExpect(jsonPath("$.lines[1].state").value("sold_out"))
                .andExpect(jsonPath("$.lines[2].state").value("unavailable"))
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.subtotal").value(10.00));
    }

    @Test
    void changingAQuantity_raisesOnlyWithinStock_butAlwaysLowers() throws Exception {
        String clock = product("65.00", 3);
        String token = guestAdd(null, clock, 3).getResponse().getCookie(CartController.GUEST_COOKIE).getValue();

        mockMvc.perform(json(withCookie(put("/cart/items/{id}", clock), token), null, """
                        {"quantity":4}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("not_enough_stock"));

        jdbc.update("UPDATE products SET stock_quantity = 1 WHERE id = ?", UUID.fromString(clock));
        mockMvc.perform(json(withCookie(put("/cart/items/{id}", clock), token), null, """
                        {"quantity":2}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.lines[0].state").value("short"));

        mockMvc.perform(json(withCookie(put("/cart/items/{id}", UUID.randomUUID()), token), null, """
                        {"quantity":1}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("cart_item_not_found"));
    }

    @Test
    void removingALine_twice_isFine() throws Exception {
        String clock = product("65.00", 3);
        String token = guestAdd(null, clock, 1).getResponse().getCookie(CartController.GUEST_COOKIE).getValue();
        mockMvc.perform(withCookie(delete("/cart/items/{id}", clock), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(0)));
        mockMvc.perform(withCookie(delete("/cart/items/{id}", clock), token)).andExpect(status().isOk());
    }

    // ---- race ----

    /**
     * Two adds of 3 against a stock of 5, released together. A read-check-write in Java would let
     * both read 0, both pass, and leave 6 in the line. The conditional upsert lets exactly one in.
     */
    @Test
    void concurrentAdds_toOneLine_neverGoPastTheStock() throws Exception {
        String clock = product("65.00", 5);
        // the cart exists already, so both requests race on the line, not on making the cart
        String token = guestAdd(null, product("10.00", 5), 1).getResponse().getCookie(CartController.GUEST_COOKIE).getValue();

        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Callable<Integer> call = () -> {
                    go.await();
                    return guestAdd(token, clock, 3).getResponse().getStatus();
                };
                results.add(pool.submit(call));
            }
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        Integer quantity = jdbc.queryForObject("SELECT quantity FROM cart_items WHERE product_id = ?",
                Integer.class, UUID.fromString(clock));
        assertThat(quantity).isEqualTo(3);
    }

    // ---- recommendations ----

    @Test
    void recommendations_stayInTheGame_putAffordableAndOtherShelvesFirst_andSkipTheCartAndSoldOut() throws Exception {
        String s = suffix();
        int game = createCategory(admin, "Game " + s, "game-" + s, null);
        int boards = createCategory(admin, "Boards " + s, "boards-" + s, game);
        int clocks = createCategory(admin, "Clocks " + s, "clocks-" + s, game);
        int otherGame = createCategory(admin, "Other " + s, "other-" + s, null);
        String inCart = product("50.00", 5, clocks);
        String sameShelf = product("40.00", 5, clocks);
        String otherShelf = product("30.00", 5, boards);
        String soldOut = product("20.00", 0, boards);
        String pricey = product("500.00", 5, boards);
        String elsewhere = product("10.00", 5, otherGame);

        String token = guestAdd(null, inCart, 1).getResponse().getCookie(CartController.GUEST_COOKIE).getValue();
        String body = mockMvc.perform(withCookie(get("/cart/recommendations"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(otherShelf, sameShelf, pricey)))
                .andExpect(jsonPath("$[*].id", not(hasItem(soldOut))))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(body, "$[*].id")).doesNotContain(inCart, elsewhere);

        mockMvc.perform(get("/cart/recommendations")).andExpect(jsonPath("$", hasSize(0)));
    }

    // ---- merge at sign-in ----

    private String cookieOf(MvcResult result) {
        return result.getResponse().getCookie(CartController.GUEST_COOKIE).getValue();
    }

    @Test
    void signingIn_mergesTheGuestCart_keepingTheLargerQuantity_andClearsTheCookie() throws Exception {
        String clock = product("65.00", 5);
        String board = product("24.00", 5);
        String email = registerUser();
        mockMvc.perform(json(post("/cart/items"), tokenFor(email), add(clock, 1))).andExpect(status().isOk());
        String guest = cookieOf(guestAdd(null, clock, 2));
        guestAdd(guest, board, 1);

        MvcResult merged = mockMvc.perform(withCookie(auth(post("/cart/merge"), tokenFor(email)), guest))
                .andExpect(status().isOk())
                // the larger of 1 and 2, not 3: signing in never quietly adds to a line
                .andExpect(jsonPath("$.lines[0].productId").value(clock))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.lines[1].productId").value(board))
                .andExpect(jsonPath("$.lines[1].quantity").value(1))
                .andReturn();
        assertThat(merged.getResponse().getCookie(CartController.GUEST_COOKIE).getMaxAge()).isZero();

        // the guest cart is gone, so signing out later doesn't bring it back
        mockMvc.perform(withCookie(get("/cart"), guest)).andExpect(jsonPath("$.lines", hasSize(0)));
        mockMvc.perform(auth(get("/cart"), tokenFor(email))).andExpect(jsonPath("$.itemCount").value(3));
    }

    @Test
    void theMerge_neverLowersTheAccountsLine() throws Exception {
        String clock = product("65.00", 5);
        String email = registerUser();
        mockMvc.perform(json(post("/cart/items"), tokenFor(email), add(clock, 3))).andExpect(status().isOk());
        String guest = cookieOf(guestAdd(null, clock, 1));

        mockMvc.perform(withCookie(auth(post("/cart/merge"), tokenFor(email)), guest))
                .andExpect(jsonPath("$.lines[0].quantity").value(3));
    }

    @Test
    void theMerge_needsAnAccount_andWithoutAGuestCartIsJustARead() throws Exception {
        String clock = product("65.00", 5);
        String guest = cookieOf(guestAdd(null, clock, 1));
        mockMvc.perform(withCookie(post("/cart/merge"), guest)).andExpect(status().isUnauthorized());

        String email = registerUser();
        mockMvc.perform(auth(post("/cart/merge"), tokenFor(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(0)))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    // ---- expiry ----

    @Test
    void theCleanup_deletesOldGuestCarts_andNeverAnAccountCart() throws Exception {
        String clock = product("65.00", 5);
        String token = guestAdd(null, clock, 1).getResponse().getCookie(CartController.GUEST_COOKIE).getValue();
        String user = customerToken();
        mockMvc.perform(json(post("/cart/items"), user, add(clock, 1)));
        jdbc.update("UPDATE carts SET updated_at = now() - interval '31 days' WHERE id IN (SELECT cart_id FROM cart_items WHERE product_id = ?)",
                UUID.fromString(clock));

        cleanup.deleteExpiredGuestCarts();

        mockMvc.perform(withCookie(get("/cart"), token)).andExpect(jsonPath("$.lines", hasSize(0)));
        mockMvc.perform(auth(get("/cart"), user)).andExpect(jsonPath("$.lines", hasSize(1)));
    }
}
