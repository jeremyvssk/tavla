// The calls this app makes to a payment provider; StripePaymentGateway in the app, a mock in tests.
package com.iloveshopping.payment;

import com.iloveshopping.payment.exception.PaymentUnavailableException;

import java.util.UUID;

/**
 * Every method throws {@link PaymentUnavailableException} when the provider can't be reached or
 * refuses the call. Only {@link #refund} moves money, and only back to the card it came from: a
 * payment is confirmed by the shopper's browser, with the card typed into the provider's own form.
 */
public interface PaymentGateway {

    /** A payment the provider is waiting on; the browser confirms it with {@code clientSecret}. */
    record Intent(String id, String clientSecret) {
    }

    /** Where a payment stands at the provider right now, and the card it was paid with, if any. */
    record IntentState(Status status, String cardBrand, String cardLast4) {

        public enum Status {
            /** waiting for a card, or for the shopper to finish the bank's check */
            OPEN,
            /** the bank is still answering; can't be cancelled now */
            PROCESSING,
            SUCCEEDED,
            CANCELED
        }
    }

    /** Idempotent per order: asking twice for the same order returns the same payment. */
    Intent create(UUID orderId, String orderNumber, long amountCents, String currency);

    String clientSecret(String intentId);

    IntentState lookUp(String intentId);

    /** Throws when it can't be cancelled, for instance because it succeeded a moment ago. */
    void cancel(String intentId);

    /** Where a refund stands right after asking for it. A refund the provider declines is thrown instead. */
    enum RefundStatus {
        /** the money is on its way back */
        SUCCEEDED,
        /** accepted; the provider confirms later (Stripe's charge.refunded webhook) */
        PENDING
    }

    /**
     * Gives the whole payment back. Idempotent per order: asking twice, after a timeout say, refunds once,
     * and a payment that was already refunded answers {@link RefundStatus#SUCCEEDED}.
     */
    RefundStatus refund(String intentId, UUID orderId);

    /**
     * Gives part of a payment back: {@code amountCents} of it. Idempotent per refund: asking twice for the same
     * {@code refundId} refunds once, so a retry after a timeout can't give the money back twice.
     */
    RefundStatus refundPart(String intentId, UUID refundId, long amountCents);

    /** The order a payment was made for, from what {@link #create} stored with it; null when it isn't one of ours. */
    UUID orderIdOf(String intentId);
}
