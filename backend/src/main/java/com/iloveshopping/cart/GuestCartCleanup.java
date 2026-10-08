// Scheduled job that deletes guest carts nobody has touched for the guest TTL (app.cart.guest-ttl).
package com.iloveshopping.cart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * The guest's cookie expires at the same age, so a deleted cart is one no browser can name any more.
 * Account carts are never deleted here. One DELETE, so every instance can run it safely at once.
 */
@Component
public class GuestCartCleanup {

    private static final Logger log = LoggerFactory.getLogger(GuestCartCleanup.class);

    private final CartRepository carts;
    private final Duration guestTtl;

    public GuestCartCleanup(CartRepository carts, @Value("${app.cart.guest-ttl}") String guestTtl) {
        this.carts = carts;
        this.guestTtl = Duration.parse(guestTtl);
    }

    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT1H")
    @Transactional
    public void deleteExpiredGuestCarts() {
        int deleted = carts.deleteGuestCartsUntouchedSince(Instant.now().minus(guestTtl));
        if (deleted > 0) {
            log.info("Deleted {} guest carts untouched for {}", deleted, guestTtl);
        }
    }
}
