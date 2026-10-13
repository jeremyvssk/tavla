// Consumes the contact queue and emails each message to the shop's inbox, with the sender as reply-to.
package com.iloveshopping.support;

import com.iloveshopping.config.MessagingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Plain text, so nothing a sender typed becomes markup in the inbox. Sent from the shop's own address with
 * the sender as reply-to: pressing Reply answers the shopper, and the shop never sends mail as someone else.
 * A failed send throws, so the message is retried and then parked in the dead letter queue.
 */
@Component
public class ContactMailListener {

    private static final Logger log = LoggerFactory.getLogger(ContactMailListener.class);
    private static final Map<String, String> TOPICS = Map.of("order", "An order", "return", "A return", "product", "A product question",
            "payment", "Payment", "other", "Something else");

    private final JavaMailSender mailSender;
    private final String from;
    private final String inbox;
    private final ZoneId zone;

    public ContactMailListener(JavaMailSender mailSender, @Value("${app.mail.from}") String from,
                               @Value("${app.support.inbox}") String inbox, @Value("${app.orders.time-zone}") String zone) {
        this.mailSender = mailSender;
        this.from = from;
        this.inbox = inbox;
        this.zone = ZoneId.of(zone);
    }

    @RabbitListener(queues = MessagingConfig.CONTACT_QUEUE)
    public void onMessage(ContactMessage m) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(inbox);
        mail.setReplyTo(m.email());
        String topic = TOPICS.getOrDefault(m.topic(), m.topic());
        mail.setSubject("Contact form: " + topic + (m.orderNumber() == null ? "" : ", " + m.orderNumber()) + ", from " + m.name());
        mail.setText("From: " + m.name() + " <" + m.email() + ">\n"
                + "Topic: " + topic + "\n"
                + (m.orderNumber() == null ? "" : "Order: " + m.orderNumber() + "\n")
                + "Sent: " + DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.ENGLISH).withZone(zone).format(m.receivedAt()) + "\n\n"
                + m.message() + "\n\n"
                + "Reply to this email to answer them.");
        mailSender.send(mail);
        // no name or address in the log: the email is the one copy
        log.info("Sent a contact message ({}) to the shop's inbox", m.topic());
    }
}
