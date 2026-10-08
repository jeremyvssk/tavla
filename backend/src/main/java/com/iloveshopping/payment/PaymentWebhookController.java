// Stripe's webhook: checks the signature, then puts the payment's outcome on the queue for the order side.
package com.iloveshopping.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, because Stripe has no token; the Stripe-Signature header is what proves the call came from
 * Stripe. The body is taken as raw bytes: the signature covers the exact bytes sent, and parsing and
 * re-serialising them first would break it. A bad signature answers 400 and publishes nothing.
 *
 * <p>This side only checks and publishes, then answers at once; PaymentEventListener in the order
 * domain applies the event from the queue. A queue that can't take it answers 503, so Stripe retries.
 */
@RestController
@RequestMapping("/payments")
public class PaymentWebhookController {

    private final PaymentService paymentService;
    private final PaymentEventPublisher publisher;

    public PaymentWebhookController(PaymentService paymentService, PaymentEventPublisher publisher) {
        this.paymentService = paymentService;
        this.publisher = publisher;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody byte[] body,
                                        @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        paymentService.parseWebhook(body, signature).ifPresent(publisher::publish);
        // 200 for events this shop ignores too, or Stripe would keep retrying them for days
        return ResponseEntity.ok().build();
    }
}
