// A PaymentGateway that answers at once and moves no money, for the load test's stack only (app.payments.fake).
package com.iloveshopping.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stripe's test mode limits how fast it may be called, so a load test against it measures Stripe, not this
 * shop. This stands in for it: a payment is "pi_fake_" plus the order id, so asking twice for the same order
 * gives the same payment (the real one is idempotent the same way) and nothing needs remembering. A payment
 * stays OPEN, as if the shopper never typed a card, so OrderExpiry releases the stock when the hold ends.
 */
@Component
@ConditionalOnProperty(name = "app.payments.fake", havingValue = "true")
public class FakePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(FakePaymentGateway.class);
    private static final String PREFIX = "pi_fake_";

    public FakePaymentGateway() {
        log.warn("Payments are FAKE (PAYMENTS_FAKE=true): orders are placed but no card is ever charged. Load test only.");
    }

    @Override
    public Intent create(UUID orderId, String orderNumber, long amountCents, String currency) {
        String id = PREFIX + orderId;
        return new Intent(id, clientSecret(id));
    }

    @Override
    public String clientSecret(String intentId) {
        return intentId + "_secret_fake";
    }

    @Override
    public IntentState lookUp(String intentId) {
        return new IntentState(IntentState.Status.OPEN, null, null);
    }

    @Override
    public void cancel(String intentId) {
    }

    @Override
    public RefundStatus refund(String intentId, UUID orderId) {
        return RefundStatus.SUCCEEDED;
    }

    @Override
    public RefundStatus refundPart(String intentId, UUID refundId, long amountCents) {
        return RefundStatus.SUCCEEDED;
    }

    @Override
    public UUID orderIdOf(String intentId) {
        if (intentId == null || !intentId.startsWith(PREFIX)) {
            return null;
        }
        try {
            return UUID.fromString(intentId.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
