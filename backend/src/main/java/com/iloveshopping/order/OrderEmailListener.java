// Consumes the order-email queue and sends each email.
package com.iloveshopping.order;

import com.iloveshopping.config.MessagingConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * A mail server that is down makes this throw: the email is retried, then parked in the dead letter
 * queue, where it can be moved back once the server is up. Payments carry on meanwhile; they are on
 * a queue of their own.
 */
@Component
public class OrderEmailListener {

    private final OrderMail orderMail;

    public OrderEmailListener(OrderMail orderMail) {
        this.orderMail = orderMail;
    }

    @RabbitListener(queues = MessagingConfig.EMAILS_QUEUE)
    public void onNotice(OrderNotice notice) {
        orderMail.send(notice);
    }
}
