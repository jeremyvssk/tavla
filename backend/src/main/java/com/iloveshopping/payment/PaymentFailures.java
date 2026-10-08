// Turns Stripe's decline codes into one sentence a shopper can act on.
package com.iloveshopping.payment;

import java.util.Map;

/**
 * Stripe's own advice is followed for lost and stolen cards: the shopper hears a plain "declined",
 * because telling someone holding a stolen card that it was reported helps only them.
 */
public final class PaymentFailures {

    private static final String DECLINED = "Your card was declined. Try another card, or ask your bank why.";

    private static final Map<String, String> MESSAGES = Map.ofEntries(
            Map.entry("insufficient_funds", "Your card has insufficient funds."),
            Map.entry("expired_card", "Your card has expired."),
            Map.entry("incorrect_number", "The card number is incorrect."),
            Map.entry("invalid_number", "The card number is incorrect."),
            Map.entry("incorrect_cvc", "The security code (CVC) is incorrect."),
            Map.entry("invalid_cvc", "The security code (CVC) is incorrect."),
            Map.entry("invalid_expiry_month", "The card's expiry date is incorrect."),
            Map.entry("invalid_expiry_year", "The card's expiry date is incorrect."),
            Map.entry("processing_error", "The bank couldn't process the payment. Try again in a moment."),
            Map.entry("authentication_required", "Your bank's security check wasn't completed."),
            Map.entry("payment_intent_authentication_failure", "Your bank's security check wasn't completed."));

    private PaymentFailures() {
    }

    public static String describe(String failureCode) {
        return failureCode == null ? DECLINED : MESSAGES.getOrDefault(failureCode, DECLINED);
    }
}
