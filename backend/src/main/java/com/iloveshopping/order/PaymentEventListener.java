// Consumes Stripe's payment events from the queue and applies each one to its order.
package com.iloveshopping.order;

import com.iloveshopping.config.MessagingConfig;
import com.iloveshopping.payment.PaymentEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * The message is acknowledged only when this method returns. If it throws (the database is down, say),
 * the transaction in {@link OrderService#applyPaymentEvent} rolled back, so nothing half-happened, and
 * the message is tried again; after the last retry it goes to the dead letter queue. A message that
 * arrives twice is applied once: the event id is recorded in the same transaction as the order change.
 */
@Component
public class PaymentEventListener {

    private final OrderService orderService;

    public PaymentEventListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = MessagingConfig.PAYMENT_EVENTS_QUEUE)
    public void onPaymentEvent(PaymentEvent event) {
        orderService.applyPaymentEvent(event);
    }
}
