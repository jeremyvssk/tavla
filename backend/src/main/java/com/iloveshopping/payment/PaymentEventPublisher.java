// Puts a verified Stripe event on the payment-events queue, and waits until RabbitMQ has it safe.
package com.iloveshopping.payment;

import com.iloveshopping.config.MessagingConfig;
import com.iloveshopping.payment.exception.EventQueueUnavailableException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * The webhook answers Stripe 200 only after this returns, and this returns only after the broker
 * confirmed the message (publisher confirms). If RabbitMQ is down the webhook answers 503 instead,
 * and Stripe sends the event again later: nothing is lost between Stripe and the queue.
 */
@Component
public class PaymentEventPublisher {

    private static final long CONFIRM_TIMEOUT_MS = 5_000;

    private final RabbitTemplate rabbit;

    public PaymentEventPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    public void publish(PaymentEvent event) {
        try {
            rabbit.invoke(ops -> {
                ops.convertAndSend(MessagingConfig.PAYMENTS_EXCHANGE, MessagingConfig.PAYMENT_EVENTS_KEY, event, message -> {
                    // shows in the management page, so a message in the dead letter queue can be traced to Stripe's event
                    message.getMessageProperties().setMessageId(event.eventId());
                    return message;
                });
                ops.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
                return null;
            });
        } catch (AmqpException e) {
            throw new EventQueueUnavailableException(e);
        }
    }
}
