// One order with everything its pages show: lines, money, delivery, payment and the status history.
package com.iloveshopping.order.dto;

import com.iloveshopping.catalog.dto.ImageFraming;
import com.iloveshopping.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The shape frontend/src/api/orders.ts calls OrderDetail. {@code cancellable} is the server's answer
 * to "may the shopper cancel this now", so the page never decides it. {@code tracking} stays null until
 * shipping is wired to a carrier. {@code history} is newest first.
 */
public record OrderDetailResponse(
        UUID id,
        String number,
        Instant placedAt,
        OrderStatus status,
        int itemCount,
        BigDecimal total,
        String email,
        String phone,
        ShipTo shipTo,
        String shippingMethod,
        List<Line> lines,
        BigDecimal subtotal,
        BigDecimal shippingCost,
        BigDecimal vat,
        boolean cancellable,
        PaymentInfo payment,
        String paymentFailure,
        Instant holdUntil,
        Tracking tracking,
        List<HistoryEntry> history) {

    public record ShipTo(String firstName, String lastName, String street, String flat, String postcode, String city,
                         String country) {
    }

    public record Line(UUID productId, String name, String options, String imageUrl, ImageFraming imageFraming,
                       BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
    }

    /** Present once paid, and only when Stripe said which card; never more than the brand and last 4. */
    public record PaymentInfo(String brand, String last4, Instant paidAt) {
    }

    public record Tracking(String carrier, String number, String url, Instant lastUpdate) {
    }

    public record HistoryEntry(Instant at, OrderStatus status, String note) {
    }
}
