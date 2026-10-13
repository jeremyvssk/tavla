// Payments on the server side: start an order's Stripe payment, read webhooks, and keep the payments table current.
package com.iloveshopping.payment;

import com.iloveshopping.crypto.FieldCipher;
import com.iloveshopping.payment.dto.PaymentSummary;
import com.iloveshopping.payment.exception.InvalidWebhookSignatureException;
import com.iloveshopping.payment.exception.PaymentUnavailableException;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeError;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The methods that call Stripe ({@link #start}, {@link #lookUp}, {@link #cancel}, {@link #refund}) run
 * outside any database transaction, so a slow Stripe never holds a row lock. The record* methods run
 * inside the order's transaction (MANDATORY), so the payment row and the order's status change commit
 * together. The Stripe id, the card and the decline code are encrypted before they reach the table.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    static final String SUCCEEDED = "payment_intent.succeeded";
    static final String FAILED = "payment_intent.payment_failed";
    static final String REFUNDED = "charge.refunded";

    private static final Map<String, String> BRANDS = Map.of(
            "visa", "Visa", "mastercard", "Mastercard", "amex", "American Express", "discover", "Discover",
            "jcb", "JCB", "diners", "Diners Club", "unionpay", "UnionPay", "cartes_bancaires", "Cartes Bancaires");

    private final PaymentGateway gateway;
    private final PaymentRepository payments;
    private final ProcessedPaymentEventRepository events;
    private final TransactionTemplate tx;
    private final FieldCipher cipher;
    private final String webhookSecret;
    private final String currency;

    public PaymentService(PaymentGateway gateway, PaymentRepository payments, ProcessedPaymentEventRepository events,
                          TransactionTemplate tx, FieldCipher cipher, @Value("${app.stripe.webhook-secret}") String webhookSecret,
                          @Value("${app.stripe.currency}") String currency) {
        this.gateway = gateway;
        this.payments = payments;
        this.events = events;
        this.tx = tx;
        this.cipher = cipher;
        this.webhookSecret = webhookSecret;
        this.currency = currency;
    }

    /**
     * The client secret the browser confirms the order's payment with. The first call creates the
     * PaymentIntent; later calls (another card after a decline) reuse it, so one order is never two payments.
     */
    public String start(UUID orderId, String orderNumber, BigDecimal amount) {
        Optional<Payment> existing = payments.findByOrderId(orderId);
        if (existing.isPresent()) {
            return gateway.clientSecret(existing.get().getProviderPaymentId());
        }
        PaymentGateway.Intent intent = gateway.create(orderId, orderNumber, cents(amount), currency);
        tx.executeWithoutResult(status ->
                payments.insertStarted(UUID.randomUUID(), orderId, cipher.encrypt(intent.id()), amount, currency.toUpperCase(Locale.ROOT)));
        return intent.clientSecret();
    }

    /** Where the order's payment stands at Stripe; empty when it never got as far as Stripe. */
    public Optional<PaymentGateway.IntentState> lookUp(UUID orderId) {
        return payments.findByOrderId(orderId).map(p -> gateway.lookUp(p.getProviderPaymentId()));
    }

    /** Cancels the order's PaymentIntent at Stripe, so nobody can pay for an order whose stock went back on sale. */
    public void cancel(UUID orderId) {
        payments.findByOrderId(orderId).ifPresent(p -> gateway.cancel(p.getProviderPaymentId()));
    }

    /**
     * Asks Stripe to give the order's payment back, once the order is cancelled (REFUND_DUE). Null when
     * there is nothing to ask: no payment, or Stripe already accepted the refund. Throws
     * {@link PaymentUnavailableException} when Stripe can't be reached; the payment stays REFUND_DUE and
     * RefundRetry asks again, with the same idempotency key, so the card is refunded once.
     */
    public PaymentGateway.RefundStatus refund(UUID orderId) {
        Optional<Payment> payment = payments.findByOrderId(orderId).filter(p -> p.getStatus() == PaymentStatus.REFUND_DUE);
        if (payment.isEmpty()) {
            return null;
        }
        PaymentGateway.RefundStatus status = gateway.refund(payment.get().getProviderPaymentId(), orderId);
        tx.executeWithoutResult(s -> payments.markRefunding(orderId));
        return status;
    }

    /**
     * Gives part of the order's payment back. Outside any transaction, like every Stripe call. Throws
     * {@link PaymentUnavailableException} when Stripe can't be reached or there is no paid payment.
     */
    public PaymentGateway.RefundStatus refundPart(UUID orderId, UUID refundId, BigDecimal amount) {
        Payment payment = payments.findByOrderId(orderId).filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED)
                .orElseThrow(() -> new PaymentUnavailableException("order " + orderId + " has no paid payment to refund", null));
        return gateway.refundPart(payment.getProviderPaymentId(), refundId, cents(amount));
    }

    /** Cancelled orders whose whole refund Stripe hasn't confirmed for at least {@code age}: the staff's "refund pending" list. */
    @Transactional(readOnly = true)
    public List<UUID> unconfirmedRefunds(Duration age) {
        return payments.findUnconfirmedRefunds(age.toSeconds());
    }

    /** Orders whose refund Stripe hasn't accepted for at least {@code age}, oldest first. */
    public List<UUID> refundsDue(Duration age, int limit) {
        return payments.findRefundsDue(age.toSeconds(), limit);
    }

    /**
     * Checks the signature and reads the event. Empty for an event this shop doesn't act on: another
     * type, a partial refund made in Stripe's dashboard, or a payment that isn't one of our orders (the
     * Stripe CLI forwards every event of the test account, other projects' too).
     */
    public Optional<PaymentEvent> parseWebhook(byte[] body, String signature) {
        if (webhookSecret.isBlank()) {
            log.warn("Refused a webhook: STRIPE_WEBHOOK_SECRET is not set");
            throw new InvalidWebhookSignatureException();
        }
        if (signature == null) {
            throw new InvalidWebhookSignatureException();
        }
        Event event;
        try {
            event = Webhook.constructEvent(new String(body, StandardCharsets.UTF_8), signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            // checked before the body is parsed, so a body nobody signed is never read
            throw new InvalidWebhookSignatureException();
        }
        Instant created = Instant.ofEpochSecond(event.getCreated());
        if (REFUNDED.equals(event.getType())) {
            return refundEvent(event, created);
        }
        PaymentEvent.Kind kind = switch (event.getType()) {
            case SUCCEEDED -> PaymentEvent.Kind.SUCCEEDED;
            case FAILED -> PaymentEvent.Kind.FAILED;
            default -> null;
        };
        if (kind == null || !(paymentObject(event) instanceof PaymentIntent intent)) {
            return Optional.empty();
        }
        UUID orderId = orderIdOf(intent);
        if (orderId == null) {
            log.info("Ignored Stripe event {}: PaymentIntent {} is not one of this shop's orders", event.getId(), intent.getId());
            return Optional.empty();
        }
        String failureCode = null;
        if (kind == PaymentEvent.Kind.FAILED) {
            StripeError error = intent.getLastPaymentError();
            // the decline code is the specific one (insufficient_funds); the code alone is often just card_declined
            failureCode = error == null ? null : error.getDeclineCode() != null ? error.getDeclineCode() : error.getCode();
        }
        return Optional.of(new PaymentEvent(event.getId(), kind, orderId, created, failureCode));
    }

    /**
     * charge.refunded carries a Charge, not the PaymentIntent with our order id, so Stripe is asked whose
     * payment it is. If Stripe can't answer, this throws and the webhook answers 503: Stripe sends the
     * event again later. A partial refund (only ever made by hand in the dashboard) isn't acted on.
     */
    private Optional<PaymentEvent> refundEvent(Event event, Instant created) {
        if (!(paymentObject(event) instanceof Charge charge) || !Boolean.TRUE.equals(charge.getRefunded())
                || charge.getPaymentIntent() == null) {
            return Optional.empty();
        }
        UUID orderId = gateway.orderIdOf(charge.getPaymentIntent());
        if (orderId == null) {
            log.info("Ignored Stripe event {}: the refunded charge is not one of this shop's orders", event.getId());
            return Optional.empty();
        }
        return Optional.of(new PaymentEvent(event.getId(), PaymentEvent.Kind.REFUNDED, orderId, created, null));
    }

    /** True the first time an event id is seen. Inside the order's transaction, so a rollback forgets it again. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstDelivery(String eventId, PaymentEvent.Kind kind) {
        return events.insertIfNew(eventId, kind.name()) == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSucceeded(UUID orderId, String cardBrand, String cardLast4) {
        payments.markSucceeded(orderId, cipher.encrypt(cardBrand), cipher.encrypt(cardLast4));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordFailed(UUID orderId, String failureCode) {
        payments.markFailed(orderId, cipher.encrypt(failureCode));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordRestarted(UUID orderId) {
        payments.markRestarted(orderId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCanceled(UUID orderId) {
        payments.markCanceled(orderId);
    }

    /** False when the payment isn't SUCCEEDED, so there is nothing this shop can refund on its own. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordRefundDue(UUID orderId) {
        return payments.markRefundDue(orderId) == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordRefunded(UUID orderId) {
        payments.markRefunded(orderId);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentSummary> summary(UUID orderId) {
        return payments.findByOrderId(orderId).map(p -> new PaymentSummary(p.getStatus(), brandName(p.getCardBrand()),
                p.getCardLast4(), p.getStatus() == PaymentStatus.FAILED ? PaymentFailures.describe(p.getFailureCode()) : null,
                p.getProviderPaymentId()));
    }

    /** Whole cents, so €155.00 is 15500. Throws rather than round away a fraction of a cent. */
    static long cents(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    /**
     * The event's object, read leniently: when the account's API version differs from the one this
     * library was built for, the strict read gives nothing, and the fields used here haven't changed.
     */
    private static StripeObject paymentObject(Event event) {
        EventDataObjectDeserializer data = event.getDataObjectDeserializer();
        if (data.getObject().isPresent()) {
            return data.getObject().get();
        }
        try {
            return data.deserializeUnsafe();
        } catch (EventDataObjectDeserializationException e) {
            log.warn("Could not read Stripe event {}: {}", event.getId(), e.getMessage());
            return null;
        }
    }

    private static UUID orderIdOf(PaymentIntent intent) {
        String value = intent.getMetadata() == null ? null : intent.getMetadata().get("order_id");
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String brandName(String stripeBrand) {
        if (stripeBrand == null || stripeBrand.isBlank()) {
            return null;
        }
        return BRANDS.getOrDefault(stripeBrand, stripeBrand.substring(0, 1).toUpperCase(Locale.ROOT) + stripeBrand.substring(1));
    }
}
