// An order's payment as the order page shows it: the status, and the card once paid.
package com.iloveshopping.payment.dto;

import com.iloveshopping.payment.PaymentStatus;

/** {@code cardBrand} is ready to show ("Visa"); {@code failure} is a sentence from PaymentFailures. */
public record PaymentSummary(PaymentStatus status, String cardBrand, String cardLast4, String failure) {
}
