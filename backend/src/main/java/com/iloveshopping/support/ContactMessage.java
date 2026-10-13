// A contact form message on its way to the shop's inbox: what the queue carries from the request to the mail sender.
package com.iloveshopping.support;

import java.time.Instant;

/**
 * Unlike the order emails, which carry only an order id, this carries the message itself: the shop keeps no
 * copy of contact messages (there is no table), so the queue is the only place it waits until it is sent.
 * It leaves the queue once the mail server took it, or stays in the dead letter queue if that keeps failing.
 */
public record ContactMessage(String name, String email, String topic, String orderNumber, String message, Instant receivedAt) {
}
