// IT helpers for orders and payments: carts, checkout, signed Stripe webhooks, and waiting for the queues.
package com.iloveshopping.support;

import com.iloveshopping.cart.CartOwner;
import com.iloveshopping.config.MessagingConfig;
import com.iloveshopping.payment.PaymentGateway;
import com.iloveshopping.payment.PaymentGateway.IntentState;
import com.jayway.jsonpath.JsonPath;
import com.stripe.Stripe;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stripe is a mock PaymentGateway and the mail server a mock JavaMailSender, so nothing here needs the
 * internet. Everything between them is real: each webhook is signed the way Stripe signs it and goes
 * through the real signature check, onto the real RabbitMQ queue, to the real consumer.
 *
 * <p>Only this context runs the queue consumers (AbstractIntegrationTest turns them off), so a message
 * is never picked up by another test class's cached context. Every order test extends this class with
 * the same mocks, so they all share that one context.
 */
@TestPropertySource(properties = {
        "app.stripe.webhook-secret=" + OrderTestSupport.WEBHOOK_SECRET,
        "spring.rabbitmq.listener.simple.auto-startup=true"})
public abstract class OrderTestSupport extends CatalogTestSupport {

    protected static final String WEBHOOK_SECRET = "whsec_test_secret_for_order_tests";
    protected static final Duration QUEUE_WAIT = Duration.ofSeconds(15);

    @MockitoBean
    protected PaymentGateway gateway;

    @MockitoBean
    protected JavaMailSender mailSender;

    @Autowired
    protected AmqpAdmin amqpAdmin;

    protected String admin;
    protected int shelf;

    @BeforeEach
    void setUpOrders() throws Exception {
        admin = adminToken();
        shelf = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'chess-clocks'", Integer.class);
        given(gateway.create(any(), anyString(), anyLong(), anyString())).willAnswer(inv -> {
            String id = intentOf(inv.getArgument(0, UUID.class).toString());
            return new PaymentGateway.Intent(id, id + "_secret_test");
        });
        given(gateway.clientSecret(anyString())).willAnswer(inv -> inv.getArgument(0, String.class) + "_secret_test");
        given(gateway.lookUp(anyString())).willReturn(new IntentState(IntentState.Status.OPEN, null, null));
        given(gateway.refund(anyString(), any())).willReturn(PaymentGateway.RefundStatus.SUCCEEDED);
        given(gateway.orderIdOf(anyString())).willAnswer(inv -> orderOf(inv.getArgument(0, String.class)));
    }

    // ---- catalog and cart ----

    protected String product(String price, int stock) throws Exception {
        String id = createProduct(admin, "Order item " + suffix(), "for an order", price, shelf, null);
        jdbc.update("UPDATE products SET stock_quantity = ? WHERE id = ?", stock, UUID.fromString(id));
        return id;
    }

    protected int stock(String productId) {
        return jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id = ?", Integer.class, UUID.fromString(productId));
    }

    protected static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String cookie) {
        return cookie == null ? request : request.cookie(new Cookie(CartOwner.GUEST_COOKIE, cookie));
    }

    protected static String add(String productId, int quantity) {
        return """
                {"productId":"%s","quantity":%d}""".formatted(productId, quantity);
    }

    /** Adds to a guest cart; returns the cart cookie (a new one when {@code cookie} is null). */
    protected String guestAdd(String cookie, String productId, int quantity) throws Exception {
        MvcResult result = mockMvc.perform(json(as(post("/cart/items"), cookie), null, add(productId, quantity)))
                .andExpect(status().isOk()).andReturn();
        Cookie set = result.getResponse().getCookie(CartOwner.GUEST_COOKIE);
        return set == null ? cookie : set.getValue();
    }

    protected void userAdd(String token, String productId, int quantity) throws Exception {
        mockMvc.perform(json(post("/cart/items"), token, add(productId, quantity))).andExpect(status().isOk());
    }

    // ---- checkout ----

    protected static String checkout(String shippingMethod) {
        return """
                {"email":"mart.kask@example.com","phone":"+372 5876 2210","firstName":"Mart","lastName":"Kask",
                 "street":"Pärnu mnt 22","flat":null,"postcode":"10141","city":"Tallinn","country":"EE",
                 "shippingMethod":"%s"}""".formatted(shippingMethod);
    }

    protected MvcResult place(String cookie, String bearer, String body) throws Exception {
        return mockMvc.perform(json(as(post("/orders"), cookie), bearer, body)).andReturn();
    }

    protected String placeOk(String cookie, String bearer) throws Exception {
        MvcResult result = place(cookie, bearer, checkout("standard"));
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    protected String orderStatus(String orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, UUID.fromString(orderId));
    }

    protected String orderNumber(String orderId) {
        return jdbc.queryForObject("SELECT number FROM orders WHERE id = ?", String.class, UUID.fromString(orderId));
    }

    // ---- Stripe's side ----

    /** The mock gateway names each order's PaymentIntent after the order, so a webhook can be built from the order id. */
    protected static String intentOf(String orderId) {
        return "pi_" + orderId.replace("-", "");
    }

    private static UUID orderOf(String intentId) {
        String hex = intentId.substring(3);
        return UUID.fromString(hex.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
    }

    protected void paidWith(String brand, String last4) {
        given(gateway.lookUp(anyString())).willReturn(new IntentState(IntentState.Status.SUCCEEDED, brand, last4));
    }

    protected static String event(String eventId, String type, String orderId, long created, String declineCode) {
        String error = declineCode == null ? "null" : """
                {"type":"card_error","code":"card_declined","decline_code":"%s","message":"Your card was declined."}"""
                .formatted(declineCode);
        return """
                {"id":"%s","object":"event","api_version":"%s","created":%d,"type":"%s","livemode":false,
                 "data":{"object":{"id":"%s","object":"payment_intent","amount":11300,"currency":"eur",
                  "status":"%s","metadata":{"order_id":"%s"},"last_payment_error":%s}}}"""
                .formatted(eventId, Stripe.API_VERSION, created, type, intentOf(orderId),
                        declineCode == null ? "succeeded" : "requires_payment_method", orderId, error);
    }

    protected static String succeeded(String orderId) {
        return event("evt_" + UUID.randomUUID(), "payment_intent.succeeded", orderId, Instant.now().getEpochSecond(), null);
    }

    protected static String failed(String orderId, String declineCode, long created) {
        return event("evt_" + UUID.randomUUID(), "payment_intent.payment_failed", orderId, created, declineCode);
    }

    /** charge.refunded: a Charge, which names the PaymentIntent but not the order. */
    protected static String refunded(String orderId, boolean whole) {
        return """
                {"id":"evt_%s","object":"event","api_version":"%s","created":%d,"type":"charge.refunded","livemode":false,
                 "data":{"object":{"id":"ch_%s","object":"charge","amount":11300,"amount_refunded":%d,"currency":"eur",
                  "refunded":%s,"status":"succeeded","payment_intent":"%s"}}}"""
                .formatted(UUID.randomUUID(), Stripe.API_VERSION, Instant.now().getEpochSecond(), UUID.randomUUID().toString().replace("-", ""),
                        whole ? 11300 : 100, whole, intentOf(orderId));
    }

    /** Stripe's scheme: HMAC-SHA256 of "timestamp.body" with the endpoint secret, sent as t=...,v1=... */
    protected static String sign(String payload, String secret) throws Exception {
        long t = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String v1 = HexFormat.of().formatHex(mac.doFinal((t + "." + payload).getBytes(StandardCharsets.UTF_8)));
        return "t=" + t + ",v1=" + v1;
    }

    protected MvcResult webhook(String payload, String signature) throws Exception {
        MockHttpServletRequestBuilder request = post("/payments/webhook").contentType(MediaType.APPLICATION_JSON).content(payload);
        if (signature != null) {
            request.header("Stripe-Signature", signature);
        }
        return mockMvc.perform(request).andReturn();
    }

    /** Posts a signed webhook, then waits until the queue's consumer has applied it. */
    protected void deliver(String payload) throws Exception {
        assertThat(webhook(payload, sign(payload, WEBHOOK_SECRET)).getResponse().getStatus()).isEqualTo(200);
        String eventId = JsonPath.read(payload, "$.id");
        await().atMost(QUEUE_WAIT).until(() -> processed(eventId));
    }

    /**
     * Waits until every event posted so far has been consumed. One consumer takes the queue in order, so
     * once a marker event posted now is applied, everything before it was too: a duplicate that changed
     * nothing has still been looked at.
     */
    protected void drain() throws Exception {
        deliver(succeeded(UUID.randomUUID().toString()));
    }

    protected boolean processed(String eventId) {
        return jdbc.queryForObject("SELECT count(*) FROM processed_payment_events WHERE event_id = ?", Integer.class, eventId) == 1;
    }

    // ---- the payment hold ----

    /** Times are stored in UTC (hibernate.jdbc.time_zone); now() alone would be this machine's local time. */
    protected void expireHold(String orderId) {
        jdbc.update("UPDATE orders SET hold_until = (now() AT TIME ZONE 'UTC') - interval '1 minute' WHERE id = ?",
                UUID.fromString(orderId));
    }

    // ---- emails and dead letters ----

    /** Every email the mock mail server was handed whose subject names the order, oldest first. */
    protected List<SimpleMailMessage> emailsAbout(String orderNumber) {
        return Mockito.mockingDetails(mailSender).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("send"))
                .map(Invocation::getArguments)
                .filter(args -> args.length == 1 && args[0] instanceof SimpleMailMessage)
                .map(args -> (SimpleMailMessage) args[0])
                .filter(m -> m.getSubject() != null && m.getSubject().contains(orderNumber))
                .toList();
    }

    protected List<SimpleMailMessage> awaitEmails(String orderNumber, int count) {
        await().atMost(QUEUE_WAIT).until(() -> emailsAbout(orderNumber).size() >= count);
        return emailsAbout(orderNumber);
    }

    protected long waiting(String queue) {
        QueueInformation info = amqpAdmin.getQueueInfo(queue);
        return info == null ? 0 : info.getMessageCount();
    }

    protected static String deadLetters(String queue) {
        return queue + MessagingConfig.DLQ;
    }
}
