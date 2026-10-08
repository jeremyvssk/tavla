// Unit tests for the words a shopper sees for each Stripe decline, and the cents sent to Stripe.
package com.iloveshopping.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentFailuresTest {

    @Test
    void theFourFailuresTheBriefNames_eachSaySomethingTheShopperCanActOn() {
        assertThat(PaymentFailures.describe("insufficient_funds")).isEqualTo("Your card has insufficient funds.");
        assertThat(PaymentFailures.describe("incorrect_number")).isEqualTo("The card number is incorrect.");
        assertThat(PaymentFailures.describe("expired_card")).isEqualTo("Your card has expired.");
        assertThat(PaymentFailures.describe("processing_error")).startsWith("The bank couldn't process the payment.");
    }

    @Test
    void aLostOrStolenCard_hearsOnlyAPlainDecline() {
        assertThat(PaymentFailures.describe("stolen_card")).isEqualTo(PaymentFailures.describe("lost_card"))
                .isEqualTo(PaymentFailures.describe(null))
                .startsWith("Your card was declined.");
    }

    @Test
    void stripeIsAskedForWholeCents() {
        assertThat(PaymentService.cents(new BigDecimal("155.00"))).isEqualTo(15500);
        assertThat(PaymentService.cents(new BigDecimal("0.10"))).isEqualTo(10);
        assertThatThrownBy(() -> PaymentService.cents(new BigDecimal("1.005"))).isInstanceOf(ArithmeticException.class);
    }
}
