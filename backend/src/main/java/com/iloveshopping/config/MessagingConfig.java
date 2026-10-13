// RabbitMQ: the payment-events and order-email queues, a dead letter queue for each, and JSON messages.
package com.iloveshopping.config;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Two queues, one job each:
 * <ul>
 *   <li>{@value #PAYMENT_EVENTS_QUEUE}: Stripe's verified webhooks. The webhook publishes and answers
 *       Stripe at once; PaymentEventListener updates the order, its stock and its history.</li>
 *   <li>{@value #EMAILS_QUEUE}: "this order needs an email", published only after the order change
 *       commits. OrderEmailListener sends it, so a slow or broken mail server never holds up a payment.</li>
 * </ul>
 * A message that keeps failing is retried (application.yml, spring.rabbitmq.listener.simple.retry),
 * then rejected; RabbitMQ moves a rejected message to the queue's dead letter queue ({@code .dlq})
 * instead of dropping it or retrying it forever. It waits there to be read and moved back from the
 * management page. Everything is durable, so a broker restart loses nothing.
 */
@Configuration
public class MessagingConfig {

    public static final String PAYMENTS_EXCHANGE = "payments";
    public static final String PAYMENT_EVENTS_KEY = "payment.event";
    public static final String PAYMENT_EVENTS_QUEUE = "orders.payment-events";

    public static final String ORDERS_EXCHANGE = "orders";
    public static final String EMAILS_KEY = "order.email";
    public static final String EMAILS_QUEUE = "orders.emails";

    /** The contact form's messages, on their way to the shop's inbox (support/ContactMailListener). */
    public static final String SUPPORT_EXCHANGE = "support";
    public static final String CONTACT_KEY = "contact.message";
    public static final String CONTACT_QUEUE = "support.contact-messages";

    public static final String DEAD_LETTERS_EXCHANGE = "dead-letters";
    public static final String DLQ = ".dlq";

    @Bean
    public Declarables messagingTopology() {
        DirectExchange payments = new DirectExchange(PAYMENTS_EXCHANGE);
        DirectExchange orders = new DirectExchange(ORDERS_EXCHANGE);
        DirectExchange deadLetters = new DirectExchange(DEAD_LETTERS_EXCHANGE);
        Queue paymentEvents = withDeadLetters(PAYMENT_EVENTS_QUEUE);
        Queue paymentEventsDlq = QueueBuilder.durable(PAYMENT_EVENTS_QUEUE + DLQ).build();
        Queue emails = withDeadLetters(EMAILS_QUEUE);
        Queue emailsDlq = QueueBuilder.durable(EMAILS_QUEUE + DLQ).build();
        DirectExchange support = new DirectExchange(SUPPORT_EXCHANGE);
        Queue contact = withDeadLetters(CONTACT_QUEUE);
        Queue contactDlq = QueueBuilder.durable(CONTACT_QUEUE + DLQ).build();
        return new Declarables(payments, orders, deadLetters, paymentEvents, paymentEventsDlq, emails, emailsDlq,
                support, contact, contactDlq,
                BindingBuilder.bind(contact).to(support).with(CONTACT_KEY),
                BindingBuilder.bind(contactDlq).to(deadLetters).with(contactDlq.getName()),
                BindingBuilder.bind(paymentEvents).to(payments).with(PAYMENT_EVENTS_KEY),
                BindingBuilder.bind(emails).to(orders).with(EMAILS_KEY),
                BindingBuilder.bind(paymentEventsDlq).to(deadLetters).with(paymentEventsDlq.getName()),
                BindingBuilder.bind(emailsDlq).to(deadLetters).with(emailsDlq.getName()));
    }

    /** Messages as JSON, read into the listener method's parameter type; Spring Boot's JsonMapper, so times read as times. */
    @Bean
    public MessageConverter messageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    private static Queue withDeadLetters(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(DEAD_LETTERS_EXCHANGE)
                .deadLetterRoutingKey(name + DLQ)
                .build();
    }
}
