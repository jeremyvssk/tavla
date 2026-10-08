// Integration tests for the queues: the emails each payment outcome sends, and failing messages ending in a dead letter queue.
package com.iloveshopping.order;

import com.iloveshopping.config.MessagingConfig;
import com.iloveshopping.support.OrderTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

/**
 * The brief's flow, end to end: Stripe's webhook publishes the payment status to a queue, the order
 * side consumes it, updates the order and the stock, and the shopper gets an email for both outcomes.
 * And what happens when it goes wrong: a message that can't be processed is retried, then parked.
 */
class MessagingIT extends OrderTestSupport {

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    RabbitListenerEndpointRegistry registry;

    @Test
    void aPayment_isConfirmedByEmail_toTheAddressOnTheOrder() throws Exception {
        String clock = product("65.00", 4);
        String cookie = guestAdd(null, clock, 2);
        String id = placeOk(cookie, null);
        paidWith("visa", "4242");

        deliver(succeeded(id));

        SimpleMailMessage mail = awaitEmails(orderNumber(id), 1).get(0);
        assertThat(mail.getTo()).containsExactly("mart.kask@example.com");
        assertThat(mail.getSubject()).isEqualTo("Order " + orderNumber(id) + " is confirmed");
        assertThat(mail.getText())
                .contains("2 × Order item")
                .contains("Total paid: €130.00, including €25.16 VAT")
                .contains("Paid with Visa ending 4242.")
                .contains("Mart Kask\nPärnu mnt 22\n10141 Tallinn\nEstonia")
                // a guest's link is the order page they paid on; it opens with their cart cookie
                .contains("/checkout/" + id);
    }

    @Test
    void aDeclinedPayment_isEmailed_withWhyAndUntilWhenTheItemsAreHeld() throws Exception {
        String clock = product("65.00", 4);
        String token = customerToken();
        userAdd(token, clock, 1);
        String id = placeOk(null, token);

        deliver(failed(id, "insufficient_funds", Instant.now().getEpochSecond()));

        SimpleMailMessage mail = awaitEmails(orderNumber(id), 1).get(0);
        assertThat(mail.getSubject()).isEqualTo("Order " + orderNumber(id) + ": the payment didn't go through");
        assertThat(mail.getText())
                .contains("Your card has insufficient funds.")
                .contains("Nothing was charged.")
                .contains("We're holding your items until")
                .contains("(Tallinn time)")
                .contains("/account/orders/" + id);
        // the stock stays held for another card
        assertThat(stock(clock)).isEqualTo(3);
    }

    @Test
    void aMessageThatCantBeRead_isRetried_thenParkedInTheDeadLetterQueue_andTheQueueKeepsGoing() throws Exception {
        String dlq = deadLetters(MessagingConfig.PAYMENT_EVENTS_QUEUE);
        long before = waiting(dlq);
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setMessageId("poison-" + System.nanoTime());
        rabbit.send(MessagingConfig.PAYMENTS_EXCHANGE, MessagingConfig.PAYMENT_EVENTS_KEY,
                new Message("{\"this is\": not json".getBytes(StandardCharsets.UTF_8), properties));

        await().atMost(QUEUE_WAIT).until(() -> waiting(dlq) == before + 1);

        // the poison message didn't block the queue: a real payment after it is applied
        String clock = product("65.00", 4);
        String cookie = guestAdd(null, clock, 1);
        String id = placeOk(cookie, null);
        deliver(succeeded(id));
        assertThat(orderStatus(id)).isEqualTo("PAYMENT_SUCCESSFUL");

        Message parked = rabbit.receive(dlq, 5_000);
        assertThat(parked).isNotNull();
        assertThat(parked.getMessageProperties().getMessageId()).isEqualTo(properties.getMessageId());
    }

    @Test
    void whenTheMailServerIsDown_thePaymentStillCounts_andTheEmailWaitsInItsDeadLetterQueue() throws Exception {
        willThrow(new MailSendException("connection refused")).given(mailSender).send(any(SimpleMailMessage.class));
        String dlq = deadLetters(MessagingConfig.EMAILS_QUEUE);
        long before = waiting(dlq);
        String clock = product("65.00", 4);
        String cookie = guestAdd(null, clock, 1);
        String id = placeOk(cookie, null);
        paidWith("visa", "4242");

        deliver(succeeded(id));

        // the order is paid at once; the email is a separate job on a separate queue
        assertThat(orderStatus(id)).isEqualTo("PAYMENT_SUCCESSFUL");
        await().atMost(QUEUE_WAIT).until(() -> waiting(dlq) == before + 1);
        // tried three times (max-retries 2), then parked
        assertThat(emailsAbout(orderNumber(id))).hasSize(3);

        // what waits there is enough to send it later, and nothing personal: an order id and a kind
        Message parked = rabbit.receive(dlq, 5_000);
        String body = new String(parked.getBody(), StandardCharsets.UTF_8);
        Map<String, Object> fields = JsonPath.read(body, "$");
        assertThat(fields).containsOnlyKeys("orderId", "kind");
        assertThat(fields).containsEntry("orderId", id).containsEntry("kind", "PAID");
        assertThat(body).doesNotContain("Kask").doesNotContain("Pärnu").doesNotContain("4242");
    }

    @Test
    void aPaymentEventOnTheQueue_carriesNoCardOrPersonalData() throws Exception {
        String clock = product("65.00", 4);
        String cookie = guestAdd(null, clock, 1);
        String id = placeOk(cookie, null);
        paidWith("visa", "4242");

        // stop the consumer from taking it, to read the message the webhook published
        registry.stop();
        try {
            String payload = succeeded(id);
            assertThat(webhook(payload, sign(payload, WEBHOOK_SECRET)).getResponse().getStatus()).isEqualTo(200);
            Message published = rabbit.receive(MessagingConfig.PAYMENT_EVENTS_QUEUE, 5_000);
            assertThat(published).isNotNull();
            Map<String, Object> fields = JsonPath.read(new String(published.getBody(), StandardCharsets.UTF_8), "$");
            assertThat(fields).containsOnlyKeys("eventId", "kind", "orderId", "createdAt", "failureCode");
            assertThat(fields).containsEntry("kind", "SUCCEEDED").containsEntry("orderId", id);
            // put it back for the consumer, the way "move messages" does from the management page
            rabbit.send(MessagingConfig.PAYMENTS_EXCHANGE, MessagingConfig.PAYMENT_EVENTS_KEY, published);
        } finally {
            registry.start();
        }
        await().atMost(QUEUE_WAIT).until(() -> "PAYMENT_SUCCESSFUL".equals(orderStatus(id)));
        assertThat(waiting(MessagingConfig.PAYMENT_EVENTS_QUEUE)).isZero();
    }
}
