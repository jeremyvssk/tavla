// The contact form: checks the CAPTCHA and hands the message to the queue that emails it to the shop's inbox.
package com.iloveshopping.support;

import com.iloveshopping.auth.CaptchaService;
import com.iloveshopping.config.MessagingConfig;
import com.iloveshopping.support.dto.ContactRequest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Through the queue rather than sent in the request: a mail server that is down for a minute doesn't lose
 * the message or make the shopper wait, it is retried. Bots are kept out three ways: the CAPTCHA here, a
 * per-address limit (ratelimit/RateLimitFilter) and the length caps on every field (ContactRequest).
 */
@Service
public class ContactService {

    private final CaptchaService captcha;
    private final RabbitTemplate rabbit;

    public ContactService(CaptchaService captcha, RabbitTemplate rabbit) {
        this.captcha = captcha;
        this.rabbit = rabbit;
    }

    public void send(ContactRequest request) {
        captcha.verify(request.captchaToken());
        String order = request.orderNumber() == null || request.orderNumber().isBlank() ? null : request.orderNumber().strip();
        rabbit.convertAndSend(MessagingConfig.SUPPORT_EXCHANGE, MessagingConfig.CONTACT_KEY, new ContactMessage(
                request.name().strip(), request.email().strip(), request.topic(), order, request.message().strip(), Instant.now()));
    }
}
