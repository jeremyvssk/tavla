// Builds and sends the order emails: confirmation, payment failed, cancelled, refunded.
package com.iloveshopping.order;

import com.iloveshopping.order.dto.OrderDetailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Plain text, built from the order as it is now, when the email goes out. Plain text means nothing a
 * shopper typed (a name, a street) can turn into markup in someone's mail client. A send that fails
 * throws, so OrderEmailListener retries it and parks it in the dead letter queue.
 */
@Component
public class OrderMail {

    private static final Logger log = LoggerFactory.getLogger(OrderMail.class);

    private static final Map<String, String> COUNTRIES = Map.of("EE", "Estonia", "LV", "Latvia", "LT", "Lithuania", "FI", "Finland");
    private static final Map<String, String> SHIPPING = Map.of(
            "standard", "Standard courier", "express", "Express courier", "locker", "Parcel locker");

    private final OrderService orderService;
    private final JavaMailSender mailSender;
    private final String from;
    private final String baseUrl;
    private final ZoneId zone;

    public OrderMail(OrderService orderService, JavaMailSender mailSender, @Value("${app.mail.from}") String from,
                     @Value("${app.frontend.base-url}") String baseUrl, @Value("${app.orders.time-zone}") String zone) {
        this.orderService = orderService;
        this.mailSender = mailSender;
        this.from = from;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.zone = ZoneId.of(zone);
    }

    public void send(OrderNotice notice) {
        Optional<OrderService.EmailView> found = orderService.forEmail(notice.orderId());
        if (found.isEmpty()) {
            log.info("No {} email: order {} no longer exists", notice.kind(), notice.orderId());
            return;
        }
        OrderService.EmailView view = found.get();
        String link = baseUrl + (view.guest() ? "/checkout/" : "/account/orders/") + view.order().id();
        Email email = compose(notice.kind(), view, link, zone);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(view.order().email());
        message.setSubject(email.subject());
        message.setText(email.body());
        mailSender.send(message);
        log.info("Sent the {} email for order {}", notice.kind(), view.order().number());
    }

    record Email(String subject, String body) {
    }

    static Email compose(OrderNotice.Kind kind, OrderService.EmailView view, String link, ZoneId zone) {
        OrderDetailResponse o = view.order();
        String hi = "Hi " + o.shipTo().firstName() + ",\n\n";
        String card = o.payment() == null ? "the card you paid with" : o.payment().brand() + " ending " + o.payment().last4();
        String sign = "\n\nTavla";
        return switch (kind) {
            case PAID -> new Email("Order " + o.number() + " is confirmed", hi
                    + "Thank you for your order. Your payment went through, and we'll start packing it soon.\n\n"
                    + summary(o, zone) + "\n\n"
                    + (o.payment() == null ? "Paid by card." : "Paid with " + card + ".") + "\n\n"
                    + "Delivery: " + SHIPPING.getOrDefault(o.shippingMethod(), o.shippingMethod()) + ", to\n" + address(o) + "\n\n"
                    + "Your order and its status: " + link + sign);
            case PAYMENT_FAILED -> new Email("Order " + o.number() + ": the payment didn't go through", hi
                    + "We couldn't take the payment of " + euros(o.total()) + " for order " + o.number() + ". "
                    + (o.paymentFailure() == null ? "" : o.paymentFailure()) + "\n\n"
                    + "Nothing was charged. "
                    + (o.holdUntil() == null ? "" : "We're holding your items until " + time(o.holdUntil(), zone)
                            + ". To pay with another card, open your order:\n" + link + "\n\nAfter that the items go back on sale.")
                    + sign);
            case CANCELLED -> view.paid()
                    ? new Email("Order " + o.number() + " is cancelled", hi
                            + "Your order " + o.number() + " is cancelled, as you asked, and the items are back on sale.\n\n"
                            + "We've asked our payment provider to send " + euros(o.total()) + " back to " + card + ". "
                            + "Banks take 5 to 10 working days to show it, and we'll email you once the refund is sent.\n\n"
                            + "Your order: " + link + sign)
                    : new Email("Order " + o.number() + " was cancelled", hi
                            + "We didn't receive a payment for order " + o.number() + " in time, so it was cancelled and the items "
                            + "went back on sale. Nothing was charged.\n\n"
                            + "If you still want them, they're back in your cart." + sign);
            case REFUNDED -> new Email("Refund for order " + o.number() + " is on its way", hi
                    + "We've refunded " + euros(o.total()) + " for order " + o.number() + " to " + card + ". "
                    + "Banks take 5 to 10 working days to show it on your statement.\n\n"
                    + "Your order: " + link + sign);
        };
    }

    private static String summary(OrderDetailResponse o, ZoneId zone) {
        StringBuilder text = new StringBuilder("Order " + o.number() + ", placed " + time(o.placedAt(), zone) + "\n\n");
        o.lines().forEach(l -> text.append(l.quantity()).append(" × ").append(l.name())
                .append(l.options() == null ? "" : " (" + l.options() + ")").append(": ").append(euros(l.lineTotal())).append('\n'));
        text.append('\n')
                .append("Subtotal: ").append(euros(o.subtotal())).append('\n')
                .append("Shipping: ").append(o.shippingCost().signum() == 0 ? "free" : euros(o.shippingCost())).append('\n')
                .append("Total paid: ").append(euros(o.total())).append(", including ").append(euros(o.vat())).append(" VAT");
        return text.toString();
    }

    private static String address(OrderDetailResponse o) {
        OrderDetailResponse.ShipTo to = o.shipTo();
        return to.firstName() + " " + to.lastName() + "\n"
                + to.street() + (to.flat() == null ? "" : ", " + to.flat()) + "\n"
                + to.postcode() + " " + to.city() + "\n"
                + COUNTRIES.getOrDefault(to.country(), to.country());
    }

    private static String euros(BigDecimal amount) {
        return "€" + amount.setScale(2).toPlainString();
    }

    private static String time(Instant at, ZoneId zone) {
        return DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.ENGLISH).withZone(zone).format(at)
                + " (" + zone.getId().replaceFirst(".*/", "").replace('_', ' ') + " time)";
    }
}
