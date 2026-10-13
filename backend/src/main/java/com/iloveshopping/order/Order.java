// JPA entity mapping the "orders" table: who ordered, where it goes, the money, and the status.
package com.iloveshopping.order;

import com.iloveshopping.crypto.Encrypted;
import com.iloveshopping.delivery.DeliveryQuote;
import com.iloveshopping.order.exception.OrderConflictException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * No setters: an order is made by {@link #place} and its status changes only through {@link #moveTo},
 * which checks the move against {@link OrderStatus} and writes the history row. The entity is named
 * CustomerOrder because ORDER is a JPQL keyword. The contact and delivery fields are stored encrypted
 * ({@link Encrypted}); here they are plain text.
 */
@Entity(name = "CustomerOrder")
@Table(name = "orders")
@Getter
public class Order {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String number;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "guest_token_hash")
    private String guestTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Convert(converter = Encrypted.class)
    @Column(nullable = false)
    private String email;

    @Convert(converter = Encrypted.class)
    @Column(nullable = false)
    private String phone;

    @Convert(converter = Encrypted.class)
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Convert(converter = Encrypted.class)
    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Convert(converter = Encrypted.class)
    @Column(nullable = false)
    private String street;

    @Convert(converter = Encrypted.class)
    @Column
    private String flat;

    @Convert(converter = Encrypted.class)
    @Column(nullable = false)
    private String postcode;

    @Convert(converter = Encrypted.class)
    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String country;

    /** The delivery option's id (delivery_options.id); the name and days below are copied from it at placement. */
    @Column(name = "shipping_method", nullable = false)
    private String shippingMethod;

    @Column(name = "shipping_method_name", nullable = false)
    private String shippingMethodName;

    @Column(name = "shipping_days_min", nullable = false)
    private short shippingDaysMin;

    @Column(name = "shipping_days_max", nullable = false)
    private short shippingDaysMax;

    /** The invoice address, all five set or none (ck_orders_billing_whole); none means the delivery address. */
    @Convert(converter = Encrypted.class)
    @Column(name = "billing_name")
    private String billingName;

    @Convert(converter = Encrypted.class)
    @Column(name = "billing_street")
    private String billingStreet;

    @Convert(converter = Encrypted.class)
    @Column(name = "billing_postcode")
    private String billingPostcode;

    @Convert(converter = Encrypted.class)
    @Column(name = "billing_city")
    private String billingCity;

    @Column(name = "billing_country")
    private String billingCountry;

    /** Set when staff mark it shipped. */
    @Column
    private String carrier;

    @Convert(converter = Encrypted.class)
    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "shipping_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal shippingCost;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal vat;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "hold_until")
    private Instant holdUntil;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    // batched, so the order list loads the lines of a whole page in one query instead of one per order
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    @BatchSize(size = 50)
    private List<OrderLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderStatusChange> history = new ArrayList<>();

    protected Order() {
    }

    /** Who an order belongs to: an account, or a guest known by the SHA-256 of their cart cookie. */
    public record Owner(UUID userId, String guestTokenHash) {
    }

    /** The delivery details as typed at checkout, already trimmed and checked. */
    public record Delivery(String email, String phone, String firstName, String lastName, String street, String flat,
                           String postcode, String city, String country) {
    }

    /** The invoice address as typed at checkout, already trimmed and checked. */
    public record Billing(String name, String street, String postcode, String city, String country) {
    }

    /** Before the order is saved; null leaves the invoice at the delivery address. */
    void billTo(Billing billing) {
        if (billing != null) {
            billingName = billing.name();
            billingStreet = billing.street();
            billingPostcode = billing.postcode();
            billingCity = billing.city();
            billingCountry = billing.country();
        }
    }

    static Order place(String number, Owner owner, Delivery delivery, DeliveryQuote method,
                       OrderTotals totals, Instant now, Instant holdUntil) {
        Order order = new Order();
        order.number = number;
        order.userId = owner.userId();
        order.guestTokenHash = owner.guestTokenHash();
        order.email = delivery.email();
        order.phone = delivery.phone();
        order.firstName = delivery.firstName();
        order.lastName = delivery.lastName();
        order.street = delivery.street();
        order.flat = delivery.flat();
        order.postcode = delivery.postcode();
        order.city = delivery.city();
        order.country = delivery.country();
        order.shippingMethod = method.optionId();
        order.shippingMethodName = method.name();
        order.shippingDaysMin = (short) method.daysMin();
        order.shippingDaysMax = (short) method.daysMax();
        order.subtotal = totals.subtotal();
        order.shippingCost = totals.shipping();
        order.vat = totals.vat();
        order.total = totals.total();
        order.placedAt = now;
        order.holdUntil = holdUntil;
        order.status = OrderStatus.PENDING_PAYMENT;
        order.statusChangedAt = now;
        order.history.add(new OrderStatusChange(order, null, OrderStatus.PENDING_PAYMENT, OrderActor.SHOPPER, null, "Order placed.", now));
        return order;
    }

    void addLine(OrderLine line) {
        lines.add(line);
    }

    /**
     * The one gate for status changes. A move the table doesn't list, or one made by the wrong actor,
     * is refused with a 409 and changes nothing.
     */
    public void moveTo(OrderStatus next, OrderActor actor, String note, Instant now) {
        moveTo(next, actor, null, note, now);
    }

    /** A move made by a staff member ({@code actor} ADMIN), who is named on the history row. */
    public void moveTo(OrderStatus next, OrderActor actor, UUID actorUserId, String note, Instant now) {
        if (!status.allows(next, actor)) {
            throw new OrderConflictException("order_status_conflict");
        }
        history.add(new OrderStatusChange(this, status, next, actor, actorUserId, note, now));
        status = next;
        statusChangedAt = now;
        if (!next.awaitsPayment()) {
            // paid, or released: nothing is held for a payment any more
            holdUntil = null;
        }
    }

    /** The courier and parcel number, given when it is marked shipped. */
    void shipWith(String carrier, String trackingNumber) {
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
    }

    public boolean holdExpired(Instant now) {
        return holdUntil != null && !holdUntil.isAfter(now);
    }

    /** Constant-time compare on the guest hash: equal length, so nothing about it leaks through timing. */
    public boolean isOwnedBy(UUID requesterUserId, String requesterGuestTokenHash) {
        if (userId != null) {
            return userId.equals(requesterUserId);
        }
        // an order whose account was deleted belongs to nobody who can still ask for it
        return guestTokenHash != null && requesterGuestTokenHash != null && MessageDigest.isEqual(
                guestTokenHash.getBytes(StandardCharsets.UTF_8), requesterGuestTokenHash.getBytes(StandardCharsets.UTF_8));
    }

    public int itemCount() {
        return lines.stream().mapToInt(OrderLine::getQuantity).sum();
    }
}
