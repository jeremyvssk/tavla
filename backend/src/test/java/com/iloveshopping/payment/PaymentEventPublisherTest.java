// Unit tests for PaymentEventPublisher: a broker that is down or doesn't confirm makes the webhook fail, so Stripe retries.
package com.iloveshopping.payment;

import com.iloveshopping.payment.exception.EventQueueUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpTimeoutException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.net.ConnectException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PaymentEventPublisherTest {

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final PaymentEventPublisher publisher = new PaymentEventPublisher(rabbit);
    private final PaymentEvent event = new PaymentEvent("evt_1", PaymentEvent.Kind.SUCCEEDED, UUID.randomUUID(), Instant.now(), null);

    @Test
    void aBrokerThatIsDown_failsThePublish_soTheWebhookAnswers503() {
        given(rabbit.invoke(any())).willThrow(new AmqpConnectException(new ConnectException("refused")));
        assertThatThrownBy(() -> publisher.publish(event)).isInstanceOf(EventQueueUnavailableException.class);
    }

    @Test
    void aBrokerThatDoesntConfirmInTime_failsItToo() {
        given(rabbit.invoke(any())).willThrow(new AmqpTimeoutException("no confirm"));
        assertThatThrownBy(() -> publisher.publish(event)).isInstanceOf(EventQueueUnavailableException.class);
    }

    @Test
    void aConfirmedPublish_returnsNormally() {
        given(rabbit.invoke(any())).willReturn(null);
        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();
    }
}
