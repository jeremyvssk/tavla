// The PaymentGateway on Stripe's API, in test mode with the keys from .env.
package com.iloveshopping.payment;

import com.iloveshopping.payment.exception.PaymentUnavailableException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentRetrieveParams;
import com.stripe.param.RefundCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The secret key never leaves the backend. Without one, every call fails as "unavailable", so an
 * unconfigured copy of the shop refuses orders cleanly instead of half-placing them.
 */
@Component
public class StripePaymentGateway implements PaymentGateway {

    private final StripeClient client;

    public StripePaymentGateway(@Value("${app.stripe.secret-key}") String secretKey) {
        this.client = secretKey.isBlank() ? null : StripeClient.builder()
                .setApiKey(secretKey)
                // a slow Stripe should fail the request in seconds, not hold the shopper's Pay button for a minute
                .setConnectTimeout(5_000)
                .setReadTimeout(15_000)
                // retries reuse the idempotency key, so a retried create can't make a second payment
                .setMaxNetworkRetries(2)
                .build();
    }

    @Override
    public Intent create(UUID orderId, String orderNumber, long amountCents, String currency) {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountCents)
                .setCurrency(currency)
                // cards only: no redirect-based methods, so the browser stays on our page (3-D Secure opens in a frame)
                .addPaymentMethodType("card")
                .setDescription("Order " + orderNumber)
                // how the webhook finds the order again
                .putMetadata("order_id", orderId.toString())
                .putMetadata("order_number", orderNumber)
                .build();
        RequestOptions options = RequestOptions.builder().setIdempotencyKey("order-" + orderId).build();
        try {
            PaymentIntent intent = client().v1().paymentIntents().create(params, options);
            return new Intent(intent.getId(), intent.getClientSecret());
        } catch (StripeException e) {
            throw unavailable("create a PaymentIntent", e);
        }
    }

    @Override
    public String clientSecret(String intentId) {
        try {
            return client().v1().paymentIntents().retrieve(intentId).getClientSecret();
        } catch (StripeException e) {
            throw unavailable("read a PaymentIntent", e);
        }
    }

    @Override
    public IntentState lookUp(String intentId) {
        PaymentIntentRetrieveParams params = PaymentIntentRetrieveParams.builder().addExpand("latest_charge").build();
        try {
            PaymentIntent intent = client().v1().paymentIntents().retrieve(intentId, params);
            Charge charge = intent.getLatestChargeObject();
            Charge.PaymentMethodDetails.Card card = charge == null || charge.getPaymentMethodDetails() == null
                    ? null : charge.getPaymentMethodDetails().getCard();
            return new IntentState(status(intent.getStatus()),
                    card == null ? null : card.getBrand(), card == null ? null : card.getLast4());
        } catch (StripeException e) {
            throw unavailable("read a PaymentIntent", e);
        }
    }

    @Override
    public void cancel(String intentId) {
        try {
            client().v1().paymentIntents().cancel(intentId);
        } catch (StripeException e) {
            throw unavailable("cancel a PaymentIntent", e);
        }
    }

    @Override
    public RefundStatus refund(String intentId, UUID orderId) {
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(intentId)
                .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                .putMetadata("order_id", orderId.toString())
                .build();
        // one key per order: a retry after a timeout gets the same refund back instead of a second one
        RequestOptions options = RequestOptions.builder().setIdempotencyKey("refund-" + orderId).build();
        try {
            Refund refund = client().v1().refunds().create(params, options);
            return switch (refund.getStatus()) {
                case "succeeded" -> RefundStatus.SUCCEEDED;
                case "pending", "requires_action" -> RefundStatus.PENDING;
                default -> throw new PaymentUnavailableException("Stripe did not refund: the refund is " + refund.getStatus(), null);
            };
        } catch (StripeException e) {
            if ("charge_already_refunded".equals(e.getCode())) {
                return RefundStatus.SUCCEEDED;
            }
            throw unavailable("refund a PaymentIntent", e);
        }
    }

    @Override
    public UUID orderIdOf(String intentId) {
        try {
            PaymentIntent intent = client().v1().paymentIntents().retrieve(intentId);
            String value = intent.getMetadata() == null ? null : intent.getMetadata().get("order_id");
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        } catch (StripeException e) {
            throw unavailable("read a PaymentIntent", e);
        }
    }

    private static IntentState.Status status(String stripeStatus) {
        return switch (stripeStatus) {
            case "succeeded" -> IntentState.Status.SUCCEEDED;
            case "canceled" -> IntentState.Status.CANCELED;
            case "processing" -> IntentState.Status.PROCESSING;
            // requires_payment_method, requires_confirmation, requires_action, requires_capture
            default -> IntentState.Status.OPEN;
        };
    }

    private StripeClient client() {
        if (client == null) {
            throw new PaymentUnavailableException("STRIPE_SECRET_KEY is not set", null);
        }
        return client;
    }

    private static PaymentUnavailableException unavailable(String what, StripeException e) {
        // Stripe's message names the request, never a card number: card details never pass through this server
        return new PaymentUnavailableException("Stripe could not " + what + ": " + e.getMessage(), e);
    }
}
