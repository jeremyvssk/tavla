// Queues an order email for after the current transaction commits, never for a change that rolled back.
package com.iloveshopping.order;

import com.iloveshopping.config.MessagingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Sending before the commit would be the classic mistake: the commit fails, and the shopper already
 * has an email about an order that never changed. So the notice waits for the commit, then goes on the
 * email queue, where OrderEmailListener sends it with retries and a dead letter queue.
 *
 * <p>The gap that is left: the database commits and RabbitMQ is unreachable a moment later. That one
 * email is lost, and logged as an error. Closing it fully would need an outbox table polled by a job.
 */
@Component
public class OrderNotifier {

    private static final Logger log = LoggerFactory.getLogger(OrderNotifier.class);

    private final RabbitTemplate rabbit;

    public OrderNotifier(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    public void afterCommit(UUID orderId, OrderNotice.Kind kind) {
        OrderNotice notice = new OrderNotice(orderId, kind);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send(notice);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(notice);
            }
        });
    }

    private void send(OrderNotice notice) {
        try {
            rabbit.convertAndSend(MessagingConfig.ORDERS_EXCHANGE, MessagingConfig.EMAILS_KEY, notice);
        } catch (AmqpException e) {
            log.error("Order {}: the {} email could not be queued and will not be sent", notice.orderId(), notice.kind(), e);
        }
    }
}
