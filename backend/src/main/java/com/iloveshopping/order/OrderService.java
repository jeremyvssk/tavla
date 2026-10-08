// Orders: place one from the cart with its stock reserved, show and list them, pay, cancel and refund, and apply what Stripe says.
package com.iloveshopping.order;

import com.iloveshopping.auth.OpaqueTokens;
import com.iloveshopping.cart.CartOwner;
import com.iloveshopping.cart.CartService;
import com.iloveshopping.cart.exception.NotEnoughStockException;
import com.iloveshopping.catalog.ProductService;
import com.iloveshopping.catalog.dto.ProductForCart;
import com.iloveshopping.order.dto.DeliveryDetailsResponse;
import com.iloveshopping.order.dto.OrderDetailResponse;
import com.iloveshopping.order.dto.OrderListParams;
import com.iloveshopping.order.dto.OrderListResponse;
import com.iloveshopping.order.dto.PaymentStartResponse;
import com.iloveshopping.order.dto.PlaceOrderRequest;
import com.iloveshopping.order.dto.PlacedOrderResponse;
import com.iloveshopping.order.exception.OrderConflictException;
import com.iloveshopping.order.exception.OrderNotFoundException;
import com.iloveshopping.order.exception.OrderValidationException;
import com.iloveshopping.payment.PaymentEvent;
import com.iloveshopping.payment.PaymentFailures;
import com.iloveshopping.payment.PaymentGateway;
import com.iloveshopping.payment.PaymentService;
import com.iloveshopping.payment.dto.PaymentSummary;
import com.iloveshopping.payment.exception.PaymentUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stock is taken when the order is placed, not when it is paid: by the time Stripe says "paid" the
 * money has moved, and finding the shelf empty then would mean refunding someone. An unpaid order
 * holds its stock for {@code app.orders.payment-hold}, then {@link OrderExpiry} puts it back.
 *
 * <p>Placing is two short transactions with the Stripe call between them, never one long one: the
 * stock rows stay locked from the reserving UPDATE until commit, and a network call inside that
 * window would make every other buyer of those products wait on Stripe. Cancelling with a refund is
 * built the same way: commit the cancellation, then ask Stripe.
 *
 * <p>Every status change that the shopper should hear about queues its email through
 * {@link OrderNotifier}, which sends it only after the transaction commits.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final CartService cartService;
    private final ProductService productService;
    private final PaymentService paymentService;
    private final OrderNotifier notifier;
    private final TransactionTemplate tx;
    private final Duration paymentHold;
    private final ZoneId zone;

    public OrderService(OrderRepository orders, CartService cartService, ProductService productService,
                        PaymentService paymentService, OrderNotifier notifier, TransactionTemplate tx,
                        @Value("${app.orders.payment-hold}") String paymentHold,
                        @Value("${app.orders.time-zone}") String zone) {
        this.orders = orders;
        this.cartService = cartService;
        this.productService = productService;
        this.paymentService = paymentService;
        this.notifier = notifier;
        this.tx = tx;
        this.paymentHold = Duration.parse(paymentHold);
        this.zone = ZoneId.of(zone);
    }

    /**
     * 1. One transaction: reserve every line's stock, copy the cart into the order, empty those lines
     * from the cart. A line that can't be reserved rolls all of it back (409). 2. Ask Stripe for the
     * payment. 3. If Stripe can't be reached, a second transaction undoes step 1 (503): nothing was
     * charged, because only the browser can confirm a payment and it never got the secret.
     */
    public PlacedOrderResponse place(CartOwner owner, PlaceOrderRequest request) {
        CheckoutRules.check(request);
        Order order = tx.execute(status -> reserve(owner, request));
        String clientSecret;
        try {
            clientSecret = paymentService.start(order.getId(), order.getNumber(), order.getTotal());
        } catch (PaymentUnavailableException e) {
            log.warn("Order {} withdrawn, the payment could not start: {}", order.getNumber(), e.getMessage());
            tx.executeWithoutResult(status -> withdraw(order.getId()));
            throw e;
        }
        return new PlacedOrderResponse(order.getId(), order.getNumber(), order.getTotal(), order.getHoldUntil(), clientSecret);
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse get(UUID orderId, CartOwner requester) {
        return view(orders.findById(orderId).filter(o -> owns(o, requester)).orElseThrow(OrderNotFoundException::new));
    }

    /** Which statuses each filter pill on the orders page stands for. */
    enum StatusGroup {
        IN_PROGRESS(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_SUCCESSFUL, OrderStatus.PROCESSING, OrderStatus.SHIPPED),
        DELIVERED(OrderStatus.DELIVERED),
        CANCELLED(OrderStatus.CANCELLED, OrderStatus.REFUNDED),
        FAILED(OrderStatus.PAYMENT_FAILED);

        final List<OrderStatus> statuses;

        StatusGroup(OrderStatus... statuses) {
            this.statuses = List.of(statuses);
        }

        static StatusGroup of(OrderStatus status) {
            return Arrays.stream(values()).filter(g -> g.statuses.contains(status)).findFirst().orElseThrow();
        }
    }

    private static final Instant EARLIEST = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant LATEST = Instant.parse("9999-01-01T00:00:00Z");
    private static final int PREVIEW_LINES = 3;

    /**
     * The account's orders, filtered and sorted in the database and a page at a time, never all of them
     * loaded to filter here. Only ever the requester's own: the user id comes from the access token.
     */
    @Transactional(readOnly = true)
    public OrderListResponse list(UUID userId, OrderListParams params) {
        if (params.from() != null && params.to() != null && params.to().isBefore(params.from())) {
            throw new OrderValidationException(Map.of("to", "The end date is before the start date."));
        }
        Instant from = params.from() == null ? EARLIEST : startOf(params.from());
        Instant to = params.to() == null ? LATEST : startOf(params.to().plusDays(1));
        String group = params.status() == null ? "all" : params.status();
        List<String> statuses = (group.equals("all") ? Arrays.asList(OrderStatus.values())
                : StatusGroup.valueOf(group.toUpperCase(Locale.ROOT)).statuses).stream().map(Enum::name).toList();
        String sort = params.sort() == null ? "newest" : params.sort();
        int page = params.page() == null ? 0 : params.page();
        int size = params.size() == null ? 10 : params.size();

        List<UUID> ids = orders.findPageForUser(userId, statuses, from, to, sort, size, (long) page * size);
        Map<UUID, Order> loaded = orders.findAllById(ids).stream().collect(Collectors.toMap(Order::getId, Function.identity()));
        List<OrderListResponse.Summary> items = ids.stream().map(loaded::get).map(OrderService::summary).toList();
        long total = orders.countForUser(userId, statuses, from, to);

        Map<StatusGroup, Long> perGroup = new EnumMap<>(StatusGroup.class);
        long all = 0;
        for (Object[] row : orders.countByStatus(userId, from, to)) {
            long n = ((Number) row[1]).longValue();
            perGroup.merge(StatusGroup.of(OrderStatus.valueOf((String) row[0])), n, Long::sum);
            all += n;
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("all", all);
        for (StatusGroup g : StatusGroup.values()) {
            counts.put(g.name().toLowerCase(Locale.ROOT), perGroup.getOrDefault(g, 0L));
        }
        return new OrderListResponse(items, page, size, total, (int) ((total + size - 1) / size), counts);
    }

    /** What the account typed at its latest checkout, to fill in the next one; empty before the first order. */
    @Transactional(readOnly = true)
    public Optional<DeliveryDetailsResponse> lastDelivery(UUID userId) {
        return orders.findFirstByUserIdOrderByPlacedAtDesc(userId).map(o -> new DeliveryDetailsResponse(o.getEmail(),
                o.getPhone(), o.getFirstName(), o.getLastName(), o.getStreet(), o.getFlat(), o.getPostcode(), o.getCity(),
                o.getCountry()));
    }

    /** An order as its emails need it: the full view, whether a guest placed it, and whether it was ever paid. */
    public record EmailView(OrderDetailResponse order, boolean guest, boolean paid) {
    }

    @Transactional(readOnly = true)
    public Optional<EmailView> forEmail(UUID orderId) {
        return orders.findById(orderId).map(o -> new EmailView(view(o), o.getUserId() == null,
                o.getHistory().stream().anyMatch(h -> h.getToStatus() == OrderStatus.PAYMENT_SUCCESSFUL)));
    }

    /**
     * The shopper cancels a paid order before packing starts. One transaction: the status move (which
     * refuses anything but Payment successful, a 409), the stock back on sale, the payment marked as owed
     * back. Then, after the commit, Stripe is asked for the refund. If Stripe can't be reached the order
     * is still cancelled, and RefundRetry asks again. A second click finds it cancelled and changes nothing.
     */
    public OrderDetailResponse cancel(UUID orderId, CartOwner requester, String reason) {
        boolean refundDue = Boolean.TRUE.equals(tx.execute(status -> {
            Order order = orders.findForUpdate(orderId).filter(o -> owns(o, requester)).orElseThrow(OrderNotFoundException::new);
            if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.REFUNDED) {
                return false;
            }
            if (!order.getStatus().allows(OrderStatus.CANCELLED, OrderActor.SHOPPER)) {
                throw new OrderConflictException("order_not_cancellable");
            }
            String why = reason == null || reason.isBlank() ? "" : ": " + reason.strip();
            order.moveTo(OrderStatus.CANCELLED, OrderActor.SHOPPER,
                    "Cancelled by you" + why + ". The refund of " + euros(order.getTotal()) + " has been started.", Instant.now());
            releaseStock(order);
            if (!paymentService.recordRefundDue(order.getId())) {
                // paid by the order's status, but no succeeded payment row: never expected, and nothing would refund it
                log.error("Order {} was cancelled but its payment isn't marked paid: refund it from the Stripe dashboard",
                        order.getNumber());
            }
            notifier.afterCommit(order.getId(), OrderNotice.Kind.CANCELLED);
            return true;
        }));
        if (refundDue) {
            requestRefund(orderId);
        }
        // through tx, not get(): a call inside this class skips get()'s @Transactional, and the view reads lazy collections
        return tx.execute(status -> view(orders.findById(orderId).filter(o -> owns(o, requester)).orElseThrow(OrderNotFoundException::new)));
    }

    /**
     * Asks Stripe to refund a cancelled order, outside any transaction. Stripe either refunds at once
     * (test cards do), and the order is Refunded now, or accepts it as pending, and its charge.refunded
     * webhook finishes the job. Unreachable: the payment stays REFUND_DUE for RefundRetry.
     */
    public void requestRefund(UUID orderId) {
        PaymentGateway.RefundStatus status;
        try {
            status = paymentService.refund(orderId);
        } catch (PaymentUnavailableException e) {
            log.warn("Order {}: the refund will be asked for again: {}", orderId, e.getMessage());
            return;
        }
        if (status == PaymentGateway.RefundStatus.SUCCEEDED) {
            tx.executeWithoutResult(s -> orders.findForUpdate(orderId)
                    .ifPresent(order -> markRefunded(order, "Stripe refunded the payment.", Instant.now())));
        }
    }

    /**
     * Paying again after a decline, with another card, on the same order and the same PaymentIntent.
     * The order goes back to Pending payment: a new payment attempt has started.
     */
    public PaymentStartResponse startPayment(UUID orderId, CartOwner requester) {
        Order order = tx.execute(status -> {
            Order o = orders.findForUpdate(orderId).filter(x -> owns(x, requester)).orElseThrow(OrderNotFoundException::new);
            Instant now = Instant.now();
            if (!o.getStatus().awaitsPayment()) {
                throw new OrderConflictException("order_not_payable");
            }
            if (o.holdExpired(now)) {
                throw new OrderConflictException("hold_expired");
            }
            if (o.getStatus() == OrderStatus.PAYMENT_FAILED) {
                o.moveTo(OrderStatus.PENDING_PAYMENT, OrderActor.SHOPPER, "Trying another card.", now);
                paymentService.recordRestarted(o.getId());
            }
            return o;
        });
        return new PaymentStartResponse(paymentService.start(order.getId(), order.getNumber(), order.getTotal()));
    }

    /**
     * What Stripe's webhook said, taken off the queue by PaymentEventListener and applied once. The event
     * id is recorded in the same transaction as the change, so a second delivery of the same event finds
     * it and changes nothing, and a crash halfway forgets it together with everything else, and the
     * message is delivered again. For a payment, Stripe is asked which card paid before the transaction
     * starts, so no row lock waits on Stripe.
     */
    public void applyPaymentEvent(PaymentEvent event) {
        Optional<PaymentGateway.IntentState> card = event.kind() == PaymentEvent.Kind.SUCCEEDED ? cardOf(event.orderId()) : Optional.empty();
        tx.executeWithoutResult(status -> apply(event, card));
    }

    private void apply(PaymentEvent event, Optional<PaymentGateway.IntentState> card) {
        if (!paymentService.firstDelivery(event.eventId(), event.kind())) {
            log.info("Stripe event {} was already applied", event.eventId());
            return;
        }
        Optional<Order> found = orders.findForUpdate(event.orderId());
        if (found.isEmpty()) {
            log.info("Stripe event {} is for order {}, which this shop doesn't have", event.eventId(), event.orderId());
            return;
        }
        Order order = found.get();
        Instant now = Instant.now();
        switch (event.kind()) {
            case SUCCEEDED -> {
                paymentService.recordSucceeded(order.getId(), card.map(PaymentGateway.IntentState::cardBrand).orElse(null),
                        card.map(PaymentGateway.IntentState::cardLast4).orElse(null));
                markPaid(order, "Stripe confirmed the payment.", now);
            }
            case FAILED -> {
                // A failure older than the order's last status move is about an earlier attempt: the shopper has
                // started paying again since, and that attempt mustn't be shown as failed. Stripe's times are whole seconds.
                boolean stale = event.createdAt().isBefore(order.getStatusChangedAt().truncatedTo(ChronoUnit.SECONDS));
                if (order.getStatus() == OrderStatus.PENDING_PAYMENT && !stale) {
                    paymentService.recordFailed(order.getId(), event.failureCode());
                    order.moveTo(OrderStatus.PAYMENT_FAILED, OrderActor.STRIPE, PaymentFailures.describe(event.failureCode()), now);
                    notifier.afterCommit(order.getId(), OrderNotice.Kind.PAYMENT_FAILED);
                } else {
                    log.info("Ignored a payment failure for order {} in status {}", order.getNumber(), order.getStatus());
                }
            }
            case REFUNDED -> markRefunded(order, "Stripe confirmed the refund.", now);
        }
    }

    /** The card a payment was made with. Unreachable Stripe doesn't stop the order being paid; its receipt just won't name the card. */
    private Optional<PaymentGateway.IntentState> cardOf(UUID orderId) {
        try {
            return paymentService.lookUp(orderId);
        } catch (PaymentUnavailableException e) {
            log.warn("Order {} is paid, but Stripe couldn't say which card: {}", orderId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * An unpaid order whose hold ran out. Stripe is asked first, because "no webhook yet" is not "not
     * paid": a payment that succeeded is applied as paid (the status check settles what a lost webhook
     * didn't), one still at the bank waits for the next run, and an open one is cancelled at Stripe
     * before the stock goes back, so nobody can pay for items that are on sale again.
     */
    public void settleExpiredHold(UUID orderId) {
        Optional<PaymentGateway.IntentState> state;
        try {
            state = paymentService.lookUp(orderId);
            if (state.isPresent() && state.get().status() == PaymentGateway.IntentState.Status.OPEN) {
                paymentService.cancel(orderId);
            }
        } catch (PaymentUnavailableException e) {
            log.warn("Order {} keeps its hold for now: {}", orderId, e.getMessage());
            return;
        }
        PaymentGateway.IntentState.Status status = state.map(PaymentGateway.IntentState::status).orElse(null);
        if (status == PaymentGateway.IntentState.Status.PROCESSING) {
            return;
        }
        tx.executeWithoutResult(s -> {
            Order order = orders.findForUpdate(orderId).orElse(null);
            Instant now = Instant.now();
            if (order == null || !order.getStatus().awaitsPayment()) {
                return;
            }
            if (status == PaymentGateway.IntentState.Status.SUCCEEDED) {
                paymentService.recordSucceeded(orderId, state.get().cardBrand(), state.get().cardLast4());
                markPaid(order, "Stripe confirmed the payment when we checked.", now);
            } else if (order.holdExpired(now)) {
                release(order, now);
            }
        });
    }

    private Order reserve(CartOwner owner, PlaceOrderRequest request) {
        CartService.OrderableCart cart = cartService.lockForOrder(owner)
                .filter(c -> !c.lines().isEmpty())
                .orElseThrow(() -> new OrderConflictException("cart_empty"));
        Map<UUID, ProductForCart> products = productService.forCart(
                cart.lines().stream().map(CartService.OrderableLine::productId).toList());
        // Lines that sold out or stopped selling stay in the cart, flagged; the checkout showed them as not in the total.
        List<CartService.OrderableLine> buyable = cart.lines().stream().filter(line -> {
            ProductForCart p = products.get(line.productId());
            return p != null && p.active() && p.stockQuantity() > 0;
        }).toList();
        if (buyable.isEmpty()) {
            throw new OrderConflictException("cart_empty");
        }

        // In product-id order, so two orders that share products lock their rows in the same order and can't deadlock.
        buyable.stream().sorted(Comparator.comparing(CartService.OrderableLine::productId)).forEach(line -> {
            if (!productService.reserveStock(line.productId(), line.quantity())) {
                int left = productService.currentStock(line.productId());
                throw new NotEnoughStockException(left <= 0 ? "out_of_stock" : "not_enough_stock", line.productId(),
                        Math.max(0, Math.min(left, CartService.LINE_LIMIT)), line.quantity());
            }
        });

        ShippingMethod method = ShippingMethod.fromWire(request.shippingMethod());
        OrderTotals totals = OrderTotals.of(buyable.stream()
                .map(line -> OrderTotals.lineTotal(products.get(line.productId()).price(), line.quantity())).toList(), method);
        Instant now = Instant.now();
        Order order = Order.place("TV-" + orders.nextNumber(), ownerOf(owner), delivery(request), method, totals,
                now, now.plus(paymentHold));
        int position = 0;
        for (CartService.OrderableLine line : buyable) {
            ProductForCart p = products.get(line.productId());
            order.addLine(new OrderLine(order, position++, p.id(), p.name(), p.options(), p.imageUrl(), p.imageFraming(),
                    p.price(), line.quantity()));
        }
        cartService.removeLines(cart.cartId(), buyable.stream().map(CartService.OrderableLine::productId).toList());
        return orders.save(order);
    }

    /** Undoes a placement the shopper never heard about: stock back, lines back in the cart, no order left. */
    private void withdraw(UUID orderId) {
        Order order = orders.findForUpdate(orderId).orElseThrow();
        releaseStock(order);
        cartService.restoreLines(order.getUserId(), order.getGuestTokenHash(), lines(order));
        orders.delete(order);
    }

    /** An unpaid order whose hold ran out: stock back on sale, lines back in the cart for another try. */
    private void release(Order order, Instant now) {
        releaseStock(order);
        cartService.restoreLines(order.getUserId(), order.getGuestTokenHash(), lines(order));
        order.moveTo(OrderStatus.CANCELLED, OrderActor.TIMER,
                "Not paid within " + paymentHold.toMinutes() + " minutes, so the items went back on sale.", now);
        paymentService.recordCanceled(order.getId());
        notifier.afterCommit(order.getId(), OrderNotice.Kind.CANCELLED);
    }

    /** The order's stock back on the shelf, in product-id order like the reserving UPDATEs, so two can't deadlock. */
    private void releaseStock(Order order) {
        lines(order).stream().sorted(Comparator.comparing(CartService.OrderableLine::productId))
                .forEach(l -> productService.releaseStock(l.productId(), l.quantity()));
    }

    /** The lines whose product still exists; a product deleted from the catalog has nothing to put back. */
    private static List<CartService.OrderableLine> lines(Order order) {
        return order.getLines().stream()
                .filter(l -> l.getProductId() != null)
                .map(l -> new CartService.OrderableLine(l.getProductId(), l.getQuantity()))
                .toList();
    }

    private void markPaid(Order order, String note, Instant now) {
        if (order.getStatus().allows(OrderStatus.PAYMENT_SUCCESSFUL, OrderActor.STRIPE)) {
            order.moveTo(OrderStatus.PAYMENT_SUCCESSFUL, OrderActor.STRIPE, note, now);
            notifier.afterCommit(order.getId(), OrderNotice.Kind.PAID);
        } else if (order.getStatus() != OrderStatus.PAYMENT_SUCCESSFUL) {
            // only if the expiry's cancel at Stripe failed and the payment went through anyway
            log.error("Order {} was paid while {}: refund it from the Stripe dashboard", order.getNumber(), order.getStatus());
        }
    }

    private void markRefunded(Order order, String note, Instant now) {
        if (order.getStatus().allows(OrderStatus.REFUNDED, OrderActor.STRIPE)) {
            order.moveTo(OrderStatus.REFUNDED, OrderActor.STRIPE, note, now);
            paymentService.recordRefunded(order.getId());
            notifier.afterCommit(order.getId(), OrderNotice.Kind.REFUNDED);
        } else if (order.getStatus() != OrderStatus.REFUNDED) {
            // a refund made by hand in Stripe's dashboard, for an order this shop didn't cancel
            log.warn("Stripe refunded order {} while it is {}: check it in the Stripe dashboard", order.getNumber(), order.getStatus());
        }
    }

    private Instant startOf(LocalDate day) {
        return day.atStartOfDay(zone).toInstant();
    }

    private static String euros(BigDecimal amount) {
        return "€" + amount.setScale(2).toPlainString();
    }

    private static OrderListResponse.Summary summary(Order o) {
        return new OrderListResponse.Summary(o.getId(), o.getNumber(), o.getPlacedAt(), o.getStatus(), o.itemCount(), o.getTotal(),
                o.getLines().stream().limit(PREVIEW_LINES)
                        .map(l -> new OrderListResponse.Preview(l.getProductName(), l.getImageUrl(), l.getImageFraming())).toList());
    }

    private static boolean owns(Order order, CartOwner requester) {
        String guestHash = requester.isGuest() && requester.guestToken() != null ? OpaqueTokens.sha256(requester.guestToken()) : null;
        return order.isOwnedBy(requester.userId(), guestHash);
    }

    private static Order.Owner ownerOf(CartOwner owner) {
        return owner.isGuest() ? new Order.Owner(null, OpaqueTokens.sha256(owner.guestToken())) : new Order.Owner(owner.userId(), null);
    }

    private static Order.Delivery delivery(PlaceOrderRequest r) {
        String flat = r.flat() == null || r.flat().isBlank() ? null : r.flat().strip();
        return new Order.Delivery(r.email().strip(), r.phone().strip(), r.firstName().strip(), r.lastName().strip(),
                r.street().strip(), flat, r.postcode().strip().toUpperCase(Locale.ROOT), r.city().strip(), r.country());
    }

    private OrderDetailResponse view(Order order) {
        Optional<PaymentSummary> payment = paymentService.summary(order.getId());
        Instant paidAt = order.getHistory().stream()
                .filter(h -> h.getToStatus() == OrderStatus.PAYMENT_SUCCESSFUL)
                .map(OrderStatusChange::getCreatedAt).reduce((first, second) -> second).orElse(null);
        OrderDetailResponse.PaymentInfo paymentInfo = payment
                .filter(p -> paidAt != null && p.cardBrand() != null && p.cardLast4() != null)
                .map(p -> new OrderDetailResponse.PaymentInfo(p.cardBrand(), p.cardLast4(), paidAt))
                .orElse(null);
        String failure = order.getStatus() == OrderStatus.PAYMENT_FAILED ? payment.map(PaymentSummary::failure).orElse(null) : null;
        List<OrderDetailResponse.HistoryEntry> history = order.getHistory().stream()
                .sorted(Comparator.comparing(OrderStatusChange::getId).reversed())
                .map(h -> new OrderDetailResponse.HistoryEntry(h.getCreatedAt(), h.getToStatus(), h.getNote()))
                .toList();
        return new OrderDetailResponse(order.getId(), order.getNumber(), order.getPlacedAt(), order.getStatus(),
                order.itemCount(), order.getTotal(), order.getEmail(), order.getPhone(),
                new OrderDetailResponse.ShipTo(order.getFirstName(), order.getLastName(), order.getStreet(), order.getFlat(),
                        order.getPostcode(), order.getCity(), order.getCountry()),
                order.getShippingMethod().wire(),
                order.getLines().stream().map(l -> new OrderDetailResponse.Line(l.getProductId(), l.getProductName(),
                        l.getOptions(), l.getImageUrl(), l.getImageFraming(), l.getUnitPrice(), l.getQuantity(), l.getLineTotal())).toList(),
                order.getSubtotal(), order.getShippingCost(), order.getVat(),
                order.getStatus().allows(OrderStatus.CANCELLED, OrderActor.SHOPPER),
                paymentInfo, failure, order.getHoldUntil(), null, history);
    }
}
