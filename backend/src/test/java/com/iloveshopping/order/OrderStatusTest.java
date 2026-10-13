// Unit tests for the order's state machine: every allowed move with its actor, the refused ones, and the history row.
package com.iloveshopping.order;

import com.iloveshopping.delivery.DeliveryQuote;
import com.iloveshopping.order.exception.OrderConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING_PAYMENT, PAYMENT_SUCCESSFUL, STRIPE",
            "PENDING_PAYMENT, PAYMENT_FAILED, STRIPE",
            "PENDING_PAYMENT, CANCELLED, TIMER",
            "PAYMENT_FAILED, PENDING_PAYMENT, SHOPPER",
            "PAYMENT_FAILED, PAYMENT_SUCCESSFUL, STRIPE",
            "PAYMENT_FAILED, CANCELLED, TIMER",
            "PAYMENT_SUCCESSFUL, PROCESSING, ADMIN",
            "PAYMENT_SUCCESSFUL, CANCELLED, SHOPPER",
            "PAYMENT_SUCCESSFUL, CANCELLED, ADMIN",
            // staff may still cancel while packing, before it leaves
            "PROCESSING, CANCELLED, ADMIN",
            "PROCESSING, SHIPPED, ADMIN",
            "SHIPPED, DELIVERED, ADMIN",
            "CANCELLED, REFUNDED, STRIPE"})
    void theTableAllows(OrderStatus from, OrderStatus to, OrderActor actor) {
        assertThat(from.allows(to, actor)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            // only Stripe says paid: not the browser reaching a success page
            "PENDING_PAYMENT, PAYMENT_SUCCESSFUL, SHOPPER",
            // the shopper's cancelling stops once packing starts, the staff's once it has left
            "PROCESSING, CANCELLED, SHOPPER",
            "SHIPPED, CANCELLED, SHOPPER",
            "SHIPPED, CANCELLED, ADMIN",
            // money that moved can't un-move into a failure
            "PAYMENT_SUCCESSFUL, PAYMENT_FAILED, STRIPE",
            // the shopper can't skip the payment, or release their own hold early (yet)
            "PENDING_PAYMENT, PROCESSING, ADMIN",
            "PENDING_PAYMENT, CANCELLED, SHOPPER",
            // the ends are ends
            "DELIVERED, CANCELLED, ADMIN",
            "REFUNDED, PAYMENT_SUCCESSFUL, STRIPE"})
    void theTableRefuses(OrderStatus from, OrderStatus to, OrderActor actor) {
        assertThat(from.allows(to, actor)).isFalse();
    }

    @Test
    void onlyUnpaidStatuses_waitForAPayment() {
        assertThat(List.of(OrderStatus.values()).stream().filter(OrderStatus::awaitsPayment))
                .containsExactly(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_FAILED);
    }

    private static Order placed(Order.Owner owner, Instant now) {
        OrderTotals totals = OrderTotals.of(new BigDecimal("65.00"), new BigDecimal("4.90"));
        return Order.place("TV-10001", owner,
                new Order.Delivery("a@b.ee", "+372 5123 4567", "Kadri", "Saar", "Narva mnt 7", null, "10117", "Tallinn", "EE"),
                new DeliveryQuote("standard", "Standard courier", new BigDecimal("4.90"), 2, 4), totals, now, now.plusSeconds(1800));
    }

    private static Order placed(Instant now) {
        return placed(new Order.Owner(UUID.randomUUID(), null), now);
    }

    @Test
    void placing_startsPendingWithAHold_andOneHistoryRow() {
        Instant now = Instant.parse("2026-10-08T10:00:00Z");
        Order order = placed(now);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getHoldUntil()).isEqualTo(now.plusSeconds(1800));
        assertThat(order.getHistory()).singleElement().satisfies(h -> {
            assertThat(h.getFromStatus()).isNull();
            assertThat(h.getToStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(h.getActor()).isEqualTo(OrderActor.SHOPPER);
        });
    }

    @Test
    void anAllowedMove_writesHistory_andPayingEndsTheHold() {
        Instant now = Instant.parse("2026-10-08T10:00:00Z");
        Order order = placed(now);
        order.moveTo(OrderStatus.PAYMENT_SUCCESSFUL, OrderActor.STRIPE, "Stripe confirmed the payment.", now.plusSeconds(5));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_SUCCESSFUL);
        assertThat(order.getHoldUntil()).isNull();
        assertThat(order.getStatusChangedAt()).isEqualTo(now.plusSeconds(5));
        assertThat(order.getHistory()).hasSize(2).last().satisfies(h -> {
            assertThat(h.getFromStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(h.getActor()).isEqualTo(OrderActor.STRIPE);
        });
    }

    @Test
    void aFailedPayment_keepsTheHold_forAnotherCard() {
        Instant now = Instant.parse("2026-10-08T10:00:00Z");
        Order order = placed(now);
        order.moveTo(OrderStatus.PAYMENT_FAILED, OrderActor.STRIPE, "Your card has insufficient funds.", now);
        assertThat(order.getHoldUntil()).isEqualTo(now.plusSeconds(1800));
        assertThat(order.holdExpired(now.plusSeconds(1799))).isFalse();
        assertThat(order.holdExpired(now.plusSeconds(1800))).isTrue();
    }

    @Test
    void aRefusedMove_is409_andChangesNothing() {
        Instant now = Instant.parse("2026-10-08T10:00:00Z");
        Order order = placed(now);
        assertThatThrownBy(() -> order.moveTo(OrderStatus.PAYMENT_SUCCESSFUL, OrderActor.SHOPPER, null, now))
                .isInstanceOf(OrderConflictException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getHistory()).hasSize(1);
    }

    @Test
    void anAccountOrder_belongsToItsUserOnly_andAGuestOrderToItsCookieHash() {
        Order order = placed(Instant.now());
        assertThat(order.isOwnedBy(order.getUserId(), null)).isTrue();
        assertThat(order.isOwnedBy(UUID.randomUUID(), null)).isFalse();
        assertThat(order.isOwnedBy(null, "some-guest-hash")).isFalse();

        Order guest = placed(new Order.Owner(null, "hash-a"), Instant.now());
        assertThat(guest.isOwnedBy(null, "hash-a")).isTrue();
        assertThat(guest.isOwnedBy(null, "hash-b")).isFalse();
        assertThat(guest.isOwnedBy(UUID.randomUUID(), null)).isFalse();
    }
}
