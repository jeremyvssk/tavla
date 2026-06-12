// Sends transactional emails (e.g. password reset) via the configured SMTP server (MailHog in dev).
package com.iloveshopping.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String from;

    public EmailService(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    /** Emails a password-reset link. The link carries the raw single-use token. */
    public void sendPasswordReset(String to, String resetUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Reset your password");
        message.setText("""
                We received a request to reset your password.

                Click the link below to choose a new one:
                %s

                This link expires in 15 minutes. If you didn't request this, ignore this email.""".formatted(resetUrl));
        mailSender.send(message);
    }
}
