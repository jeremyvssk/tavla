// Unit tests for the order emails' wording: what each kind says, the money, the card, the hold time and the link.
package com.iloveshopping.order;

import com.iloveshopping.order.dto.OrderDetailResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMailTest {

    private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
    private static final String LINK = "https://localhost:5443/account/orders/x";

    private static OrderDetailResponse order(OrderStatus status, OrderDetailResponse.PaymentInfo payment, String failure,
                                             Instant holdUntil, String flat) {
        return new OrderDetailResponse(UUID.randomUUID(), "TV-10042", Instant.parse("2026-10-08T11:02:00Z"), status, 3,
                new BigDecimal("113.00"), "mart.kask@example.com", "+372 5876 2210",
                new OrderDetailResponse.ShipTo("Mart", "Kask", "Pärnu mnt 22", flat, "10141", "Tallinn", "EE"),
                "standard",
                List.of(new OrderDetailResponse.Line(UUID.randomUUID(), "Walnut board", "45 cm", null, null,
                                new BigDecimal("24.00"), 2, new BigDecimal("48.00")),
                        new OrderDetailResponse.Line(UUID.randomUUID(), "DGT 3000", null, null, null,
                                new BigDecimal("65.00"), 1, new BigDecimal("65.00"))),
                new BigDecimal("113.00"), new BigDecimal("0.00"), new BigDecimal("21.87"), false, payment, failure, holdUntil,
                null, List.of());
    }

    private static final OrderDetailResponse.PaymentInfo VISA =
            new OrderDetailResponse.PaymentInfo("Visa", "4242", Instant.parse("2026-10-08T11:03:00Z"));

    @Test
    void theConfirmation_listsWhatWasBought_theMoney_theCard_andWhereItGoes() {
        OrderMail.Email email = OrderMail.compose(OrderNotice.Kind.PAID,
                new OrderService.EmailView(order(OrderStatus.PAYMENT_SUCCESSFUL, VISA, null, null, "flat 4"), false, true), LINK, TALLINN);

        assertThat(email.subject()).isEqualTo("Order TV-10042 is confirmed");
        assertThat(email.body())
                .startsWith("Hi Mart,")
                // placed 11:02 UTC is 14:02 in Tallinn (summer time)
                .contains("Order TV-10042, placed 8 October 2026, 14:02 (Tallinn time)")
                .contains("2 × Walnut board (45 cm): €48.00\n1 × DGT 3000: €65.00")
                .contains("Subtotal: €113.00\nShipping: free\nTotal paid: €113.00, including €21.87 VAT")
                .contains("Paid with Visa ending 4242.")
                .contains("Delivery: Standard courier, to\nMart Kask\nPärnu mnt 22, flat 4\n10141 Tallinn\nEstonia")
                .contains(LINK);
    }

    @Test
    void aDecline_saysWhy_thatNothingWasCharged_andUntilWhenTheItemsAreHeld() {
        OrderMail.Email email = OrderMail.compose(OrderNotice.Kind.PAYMENT_FAILED, new OrderService.EmailView(
                order(OrderStatus.PAYMENT_FAILED, null, "Your card has insufficient funds.", Instant.parse("2026-10-08T11:32:00Z"), null),
                false, false), LINK, TALLINN);

        assertThat(email.subject()).isEqualTo("Order TV-10042: the payment didn't go through");
        assertThat(email.body())
                .contains("We couldn't take the payment of €113.00 for order TV-10042. Your card has insufficient funds.")
                .contains("Nothing was charged.")
                .contains("We're holding your items until 8 October 2026, 14:32 (Tallinn time)")
                .contains(LINK);
    }

    @Test
    void aCancelledPaidOrder_promisesTheRefund_andAnUnpaidOne_saysNothingWasCharged() {
        OrderMail.Email paid = OrderMail.compose(OrderNotice.Kind.CANCELLED,
                new OrderService.EmailView(order(OrderStatus.CANCELLED, VISA, null, null, null), false, true), LINK, TALLINN);
        assertThat(paid.subject()).isEqualTo("Order TV-10042 is cancelled");
        assertThat(paid.body()).contains("€113.00 back to Visa ending 4242").doesNotContain("Nothing was charged");

        OrderMail.Email unpaid = OrderMail.compose(OrderNotice.Kind.CANCELLED,
                new OrderService.EmailView(order(OrderStatus.CANCELLED, null, null, null, null), true, false), LINK, TALLINN);
        assertThat(unpaid.subject()).isEqualTo("Order TV-10042 was cancelled");
        assertThat(unpaid.body()).contains("Nothing was charged.").doesNotContain("back to");
    }

    @Test
    void theRefund_namesTheCard_orTheCardPaidWith_whenStripeDidntSay() {
        OrderMail.Email known = OrderMail.compose(OrderNotice.Kind.REFUNDED,
                new OrderService.EmailView(order(OrderStatus.REFUNDED, VISA, null, null, null), false, true), LINK, TALLINN);
        assertThat(known.body()).contains("We've refunded €113.00 for order TV-10042 to Visa ending 4242.");

        OrderMail.Email unknown = OrderMail.compose(OrderNotice.Kind.REFUNDED,
                new OrderService.EmailView(order(OrderStatus.REFUNDED, null, null, null, null), false, true), LINK, TALLINN);
        assertThat(unknown.body()).contains("to the card you paid with.");
    }
}
