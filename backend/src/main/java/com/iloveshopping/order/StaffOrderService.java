// The staff's side of orders: the queue, one order's full story, moving it along, notes, and refunds.
package com.iloveshopping.order;

import com.iloveshopping.audit.AuditEvent;
import com.iloveshopping.audit.AuditKind;
import com.iloveshopping.audit.AuditLog;
import com.iloveshopping.audit.AuditTarget;
import com.iloveshopping.auth.AuthPrincipal;
import com.iloveshopping.auth.CurrentUser;
import com.iloveshopping.csv.Csv;
import com.iloveshopping.order.dto.OrderDetailResponse;
import com.iloveshopping.order.dto.RefundRequest;
import com.iloveshopping.order.dto.StaffOrderDetailResponse;
import com.iloveshopping.order.dto.StaffOrderPageResponse;
import com.iloveshopping.order.dto.StaffOrderParams;
import com.iloveshopping.order.dto.StaffOrderRow;
import com.iloveshopping.order.dto.StartPackingRequest;
import com.iloveshopping.order.dto.StatusMoveRequest;
import com.iloveshopping.order.exception.OrderConflictException;
import com.iloveshopping.order.exception.OrderNotFoundException;
import com.iloveshopping.order.exception.OrderValidationException;
import com.iloveshopping.order.exception.RefundLimitException;
import com.iloveshopping.payment.PaymentGateway;
import com.iloveshopping.payment.PaymentService;
import com.iloveshopping.payment.PaymentStatus;
import com.iloveshopping.payment.dto.PaymentSummary;
import com.iloveshopping.payment.exception.PaymentUnavailableException;
import com.iloveshopping.user.Role;
import com.iloveshopping.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Every write locks the order's row first (findForUpdate), so two people working the same order are
 * served one after the other, and the second gets a 409 from the state machine instead of a double move.
 * Stripe is only ever called after the commit, as in OrderService: a slow Stripe never holds a row lock.
 * Each change writes an audit entry in its own transaction, so a change and its entry commit together.
 */
@Service
public class StaffOrderService {

    private static final Logger log = LoggerFactory.getLogger(StaffOrderService.class);

    /** The most Support may refund on one order, all its part refunds together; an admin has no limit. */
    static final BigDecimal SUPPORT_REFUND_LIMIT = new BigDecimal("100.00");
    /** A cancelled order whose refund Stripe hasn't confirmed for this long goes on the "Refund not confirmed" tab. */
    static final Duration REFUND_PENDING_AFTER = Duration.ofHours(1);
    private static final int EXPORT_LIMIT = 10_000;
    private static final List<OrderStatus> MOVES = List.of(OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.DELIVERED,
            OrderStatus.CANCELLED);
    private static final Instant EARLIEST = Instant.parse("1970-01-01T00:00:00Z");

    private final OrderRepository orders;
    private final OrderNoteRepository notes;
    private final RefundRepository refunds;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final UserService userService;
    private final OrderNotifier notifier;
    private final AuditLog auditLog;
    private final TransactionTemplate tx;
    private final ZoneId zone;

    public StaffOrderService(OrderRepository orders, OrderNoteRepository notes, RefundRepository refunds, OrderService orderService,
                             PaymentService paymentService, UserService userService, OrderNotifier notifier, AuditLog auditLog,
                             TransactionTemplate tx, @Value("${app.orders.time-zone}") String zone) {
        this.orders = orders;
        this.notes = notes;
        this.refunds = refunds;
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.userService = userService;
        this.notifier = notifier;
        this.auditLog = auditLog;
        this.tx = tx;
        this.zone = ZoneId.of(zone);
    }

    public StaffOrderPageResponse list(StaffOrderParams params) {
        int page = params.page() == null ? 0 : params.page();
        int size = params.size() == null ? 25 : params.size();
        return tx.execute(status -> {
            Page<Order> found = orders.findAll(filter(params, true), PageRequest.of(page, size, sort(params.sort())));
            List<StaffOrderRow> rows = found.getContent().stream().map(StaffOrderService::row).toList();
            return new StaffOrderPageResponse(rows, page, size, found.getTotalElements(), found.getTotalPages(), counts(params));
        });
    }

    /** The filtered queue, or only the ticked orders, as a CSV file; newest first, at most 10,000 rows. */
    public byte[] export(StaffOrderParams params) {
        DateTimeFormatter time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone);
        return tx.execute(status -> {
            Specification<Order> spec = params.ids() == null || params.ids().isEmpty() ? filter(params, true)
                    : (root, query, cb) -> root.get("id").in(params.ids());
            List<List<?>> rows = new ArrayList<>();
            for (Order o : orders.findAll(spec, PageRequest.of(0, EXPORT_LIMIT, sort(params.sort()))).getContent()) {
                rows.add(List.of(o.getNumber(), time.format(o.getPlacedAt()), o.getStatus().name(), customer(o),
                        o.getUserId() == null ? "guest" : "account", o.itemCount(), o.getTotal().toPlainString(),
                        o.getShippingMethodName(), time.format(o.getStatusChangedAt())));
            }
            return Csv.file(List.of("Order", "Placed", "Status", "Customer", "Checkout", "Items", "Total", "Delivery", "Status since"), rows);
        });
    }

    public StaffOrderDetailResponse detail(UUID id) {
        return tx.execute(status -> detailOf(orders.findById(id).orElseThrow(OrderNotFoundException::new)));
    }

    /**
     * One step along: Processing (packing started, the shopper can't cancel any more), Shipped (with the
     * courier and parcel number), Delivered, or Cancelled (with a reason; a paid order's money goes back).
     * The state machine (OrderStatus) decides what is allowed from where.
     */
    public StaffOrderDetailResponse move(UUID id, StatusMoveRequest request) {
        OrderStatus to = OrderStatus.valueOf(request.to());
        UUID actor = actorId();
        Map<String, String> missing = new LinkedHashMap<>();
        if (to == OrderStatus.SHIPPED && request.carrier() == null) {
            missing.put("carrier", "Choose the courier.");
        }
        if (to == OrderStatus.SHIPPED && (request.trackingNumber() == null || request.trackingNumber().length() < 4)) {
            missing.put("trackingNumber", "Type the number from the courier's label.");
        }
        if (to == OrderStatus.CANCELLED && (request.note() == null || request.note().isBlank())) {
            missing.put("note", "Say why it is cancelled.");
        }
        if (!missing.isEmpty()) {
            throw new OrderValidationException(missing);
        }
        boolean refundDue = Boolean.TRUE.equals(tx.execute(status -> {
            Order order = orders.findForUpdate(id).orElseThrow(OrderNotFoundException::new);
            OrderStatus from = order.getStatus();
            Instant now = Instant.now();
            boolean paid = false;
            switch (to) {
                case PROCESSING -> order.moveTo(to, OrderActor.ADMIN, actor, "Packing started.", now);
                case SHIPPED -> {
                    order.moveTo(to, OrderActor.ADMIN, actor, "Shipped with " + request.carrier() + ".", now);
                    order.shipWith(request.carrier(), request.trackingNumber().toUpperCase(Locale.ROOT));
                    notifier.afterCommit(id, OrderNotice.Kind.SHIPPED);
                }
                case DELIVERED -> {
                    order.moveTo(to, OrderActor.ADMIN, actor, "Delivered.", now);
                    notifier.afterCommit(id, OrderNotice.Kind.DELIVERED);
                }
                default -> paid = cancel(order, request.note().strip(), actor, now);
            }
            auditLog.record(AuditEvent.of(AuditKind.ORDERS, verb(to)).target(AuditTarget.ORDER, id, order.getNumber())
                    .change(label(from), label(to))
                    .detail(to == OrderStatus.SHIPPED ? request.carrier() : to == OrderStatus.CANCELLED ? request.note().strip() : null));
            return paid;
        }));
        if (refundDue) {
            orderService.requestRefund(id);
        }
        return detail(id);
    }

    /** Stock back on sale, the payment marked as owed back, and the shopper told; the refund is asked for after the commit. */
    private boolean cancel(Order order, String reason, UUID actor, Instant now) {
        boolean paid = order.getStatus() != OrderStatus.PENDING_PAYMENT && order.getStatus() != OrderStatus.PAYMENT_FAILED;
        order.moveTo(OrderStatus.CANCELLED, OrderActor.ADMIN, actor, "Cancelled by the shop: " + reason
                + (paid ? ". The refund of " + OrderService.euros(order.getTotal()) + " has been started." : "."), now);
        orderService.releaseStock(order);
        boolean refundDue = paid && paymentService.recordRefundDue(order.getId());
        if (paid && !refundDue) {
            log.error("Order {} was cancelled by staff but its payment isn't marked paid: refund it from the Stripe dashboard",
                    order.getNumber());
        }
        notifier.afterCommit(order.getId(), OrderNotice.Kind.CANCELLED);
        return refundDue;
    }

    public StaffOrderDetailResponse addNote(UUID id, String text) {
        UUID author = actorId();
        tx.executeWithoutResult(status -> {
            Order order = orders.findById(id).orElseThrow(OrderNotFoundException::new);
            notes.save(new OrderNote(id, author, text.strip(), Instant.now()));
            // what the note says stays on the order: it may name the customer, and the log is one more copy
            auditLog.record(AuditEvent.of(AuditKind.ORDERS, "Added a note to {target}").target(AuditTarget.ORDER, id, order.getNumber()));
        });
        return detail(id);
    }

    /**
     * The ticked orders that are still Payment successful move to Processing; the rest (cancelled by the
     * shopper a moment ago, or already moved by someone else) are counted as skipped. Rows are locked in
     * id order, so two people starting overlapping batches can't deadlock.
     */
    public StartPackingRequest.Result startPacking(List<UUID> ids) {
        UUID actor = actorId();
        return tx.execute(status -> {
            int moved = 0;
            for (UUID id : ids.stream().distinct().sorted().toList()) {
                Optional<Order> found = orders.findForUpdate(id);
                if (found.isEmpty() || found.get().getStatus() != OrderStatus.PAYMENT_SUCCESSFUL) {
                    continue;
                }
                Order order = found.get();
                order.moveTo(OrderStatus.PROCESSING, OrderActor.ADMIN, actor, "Packing started.", Instant.now());
                auditLog.record(AuditEvent.of(AuditKind.ORDERS, verb(OrderStatus.PROCESSING)).target(AuditTarget.ORDER, id, order.getNumber())
                        .change(label(OrderStatus.PAYMENT_SUCCESSFUL), label(OrderStatus.PROCESSING)));
                moved++;
            }
            return new StartPackingRequest.Result(moved, ids.size() - moved);
        });
    }

    /**
     * A part refund: some lines (how many of each) and the shipping, or either. The amount comes from the
     * order's own prices, never from the request. Under the order's row lock the request is checked against
     * what was already refunded, so two people refunding the same line at once can't give it back twice.
     * Support may refund up to {@link #SUPPORT_REFUND_LIMIT} on one order in all. The items are not put back
     * on sale: a part refund is for something broken or missing, not a return to the shelf.
     */
    public StaffOrderDetailResponse refund(UUID id, RefundRequest request) {
        AuthPrincipal by = CurrentUser.principal();
        Refund refund = tx.execute(status -> {
            Order order = orders.findForUpdate(id).orElseThrow(OrderNotFoundException::new);
            if (!List.of(OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.DELIVERED).contains(order.getStatus())
                    || paymentService.summary(id).map(PaymentSummary::status).orElse(null) != PaymentStatus.SUCCEEDED) {
                throw new OrderConflictException("order_not_refundable");
            }
            List<Refund> before = refunds.findByOrderIdOrderByCreatedAtAsc(id);
            Map<Integer, Integer> refundedPerLine = refundedPerLine(before);
            Map<String, String> problems = new LinkedHashMap<>();
            List<Refund.Line> lines = new ArrayList<>();
            BigDecimal amount = BigDecimal.ZERO;
            Set<UUID> seen = new HashSet<>();
            for (RefundRequest.Line asked : request.lines()) {
                OrderLine line = order.getLines().stream().filter(l -> asked.productId().equals(l.getProductId())).findFirst().orElse(null);
                if (line == null || !seen.add(asked.productId())) {
                    problems.put("lines", "Each product once, and only products on this order.");
                    continue;
                }
                int left = line.getQuantity() - refundedPerLine.getOrDefault((int) line.getPosition(), 0);
                if (asked.quantity() > left) {
                    problems.put("lines", left == 0 ? line.getProductName() + " was already refunded."
                            : "Only " + left + " of " + line.getProductName() + " can still be refunded.");
                    continue;
                }
                BigDecimal part = line.getUnitPrice().multiply(BigDecimal.valueOf(asked.quantity()));
                lines.add(new Refund.Line(line.getPosition(), asked.quantity(), part));
                amount = amount.add(part);
            }
            boolean shipping = request.shipping();
            if (shipping && (before.stream().anyMatch(Refund::isShipping) || order.getShippingCost().signum() == 0)) {
                problems.put("shipping", "The shipping was free or is already refunded.");
            }
            if (lines.isEmpty() && !shipping && problems.isEmpty()) {
                problems.put("lines", "Choose what to refund.");
            }
            if (!problems.isEmpty()) {
                throw new OrderValidationException(problems);
            }
            if (shipping) {
                amount = amount.add(order.getShippingCost());
            }
            BigDecimal already = before.stream().map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (by != null && by.role() == Role.SUPPORT && already.add(amount).compareTo(SUPPORT_REFUND_LIMIT) > 0) {
                throw new RefundLimitException();
            }
            Refund saved = refunds.save(new Refund(id, amount.setScale(2), shipping, request.reason().strip(),
                    by == null ? null : by.userId(), lines, Instant.now()));
            auditLog.record(AuditEvent.of(AuditKind.ORDERS, "Refunded part of {target}").target(AuditTarget.ORDER, id, order.getNumber())
                    .detail(OrderService.euros(saved.getAmount()) + ": " + saved.getReason()));
            return saved;
        });
        askStripe(refund.getId(), id, refund.getAmount());
        return detail(id);
    }

    /**
     * Asks Stripe for a part refund that is still DUE, outside any transaction. Unreachable: it stays DUE and
     * {@link RefundRetry} asks again, with the same idempotency key, so the card is refunded once.
     */
    void askStripe(UUID refundId, UUID orderId, BigDecimal amount) {
        PaymentGateway.RefundStatus status;
        try {
            status = paymentService.refundPart(orderId, refundId, amount);
        } catch (PaymentUnavailableException e) {
            log.warn("Part refund {} of order {} will be asked for again: {}", refundId, orderId, e.getMessage());
            return;
        }
        String accepted = status == PaymentGateway.RefundStatus.SUCCEEDED ? "SUCCEEDED" : "PENDING";
        tx.executeWithoutResult(s -> {
            if (refunds.markAccepted(refundId, accepted) == 1) {
                notifier.afterCommit(orderId, OrderNotice.Kind.PART_REFUNDED);
            }
        });
    }

    /** The staff overview's order numbers: today's and the week's sales, failed payments, the packing queue. */
    public record Overview(BigDecimal salesToday, long ordersToday, BigDecimal sales7d, long orders7d, long failedPaymentsToday,
                           long paymentsTriedToday, long toPack, Instant oldestToPack, long refundsUnconfirmed,
                           List<StaffOrderRow> latestOrders) {
    }

    /** "Today" and "the last 7 days" are the shop's calendar days (app.orders.time-zone), not UTC's. */
    public Overview overview() {
        Instant today = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Instant week = java.time.LocalDate.now(zone).minusDays(6).atStartOfDay(zone).toInstant();
        return tx.execute(status -> {
            Object[] day = orders.salesSince(today).getFirst();
            Object[] sevenDays = orders.salesSince(week).getFirst();
            Object[] payments = orders.paymentsSince(today).getFirst();
            List<StaffOrderRow> latest = orders.findAll(PageRequest.of(0, 5, sort("newest"))).getContent().stream()
                    .map(StaffOrderService::row).toList();
            return new Overview(money(day[0]), count(day[1]), money(sevenDays[0]), count(sevenDays[1]), count(payments[0]),
                    count(payments[1]), orders.countByStatus(OrderStatus.PAYMENT_SUCCESSFUL),
                    orders.findFirstByStatusOrderByStatusChangedAtAsc(OrderStatus.PAYMENT_SUCCESSFUL).map(Order::getStatusChangedAt).orElse(null),
                    paymentService.unconfirmedRefunds(REFUND_PENDING_AFTER).size(), latest);
        });
    }

    private static BigDecimal money(Object value) {
        return ((BigDecimal) value).setScale(2);
    }

    private static long count(Object value) {
        return ((Number) value).longValue();
    }

    /** For RefundRetry: part refunds Stripe hasn't accepted for at least {@code age}. */
    List<Refund> refundsDue(Duration age, int limit) {
        return refunds.findAllById(refunds.findDue(age.toSeconds(), limit));
    }

    private StaffOrderDetailResponse detailOf(Order order) {
        OrderDetailResponse view = orderService.view(order);
        List<OrderNote> teamNotes = notes.findByOrderIdOrderByIdAsc(order.getId());
        Set<UUID> people = new HashSet<>();
        order.getHistory().stream().map(OrderStatusChange::getActorUserId).filter(Objects::nonNull).forEach(people::add);
        teamNotes.stream().map(OrderNote::getAuthorId).filter(Objects::nonNull).forEach(people::add);
        Map<UUID, String> names = userService.namesOf(people);

        List<StaffOrderDetailResponse.TimelineEntry> timeline = new ArrayList<>();
        for (OrderStatusChange h : order.getHistory()) {
            String actor = h.getActor() == OrderActor.ADMIN ? names.getOrDefault(h.getActorUserId(), "Staff") : h.getActor().name();
            timeline.add(new StaffOrderDetailResponse.TimelineEntry(h.getCreatedAt(), h.getFromStatus(), h.getToStatus(), actor, h.getNote()));
        }
        for (OrderNote n : teamNotes) {
            timeline.add(new StaffOrderDetailResponse.TimelineEntry(n.getCreatedAt(), null, null,
                    names.getOrDefault(n.getAuthorId(), "Staff"), n.getNote()));
        }
        timeline.sort(Comparator.comparing(StaffOrderDetailResponse.TimelineEntry::at).reversed());

        List<Refund> made = refunds.findByOrderIdOrderByCreatedAtAsc(order.getId());
        Map<Integer, Integer> perLine = refundedPerLine(made);
        Map<String, Integer> refundedLines = new LinkedHashMap<>();
        for (OrderLine l : order.getLines()) {
            if (l.getProductId() != null && perLine.containsKey((int) l.getPosition())) {
                refundedLines.put(l.getProductId().toString(), perLine.get((int) l.getPosition()));
            }
        }
        BigDecimal refunded = made.stream().map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (order.getStatus() == OrderStatus.REFUNDED) {
            refunded = order.getTotal();
        }
        List<OrderStatus> next = MOVES.stream().filter(s -> order.getStatus().allows(s, OrderActor.ADMIN)).toList();
        return new StaffOrderDetailResponse(view, customer(order), order.getUserId() == null, timeline, refunded.setScale(2),
                refundedLines, made.stream().anyMatch(Refund::isShipping), next);
    }

    private static Map<Integer, Integer> refundedPerLine(List<Refund> made) {
        Map<Integer, Integer> perLine = new HashMap<>();
        made.forEach(r -> r.getLines().forEach(l -> perLine.merge((int) l.getPosition(), l.getQuantity(), Integer::sum)));
        return perLine;
    }

    private Specification<Order> filter(StaffOrderParams params, boolean withStatus) {
        Specification<Order> spec = Specification.unrestricted();
        if (params.from() != null) {
            Instant from = params.from().atStartOfDay(zone).toInstant();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("placedAt"), from));
        }
        if (params.number() != null && !params.number().isBlank()) {
            String number = params.number().strip().toUpperCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.like(root.get("number"), number));
        }
        String status = params.status() == null ? "all" : params.status();
        if (withStatus && status.equals("REFUND_PENDING")) {
            List<UUID> pending = paymentService.unconfirmedRefunds(REFUND_PENDING_AFTER);
            spec = spec.and((root, query, cb) -> pending.isEmpty() ? cb.disjunction()
                    : cb.and(cb.equal(root.get("status"), OrderStatus.CANCELLED), root.get("id").in(pending)));
        } else if (withStatus && !status.equals("all")) {
            OrderStatus exact = OrderStatus.valueOf(status);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), exact));
        }
        return spec;
    }

    private Map<String, Long> counts(StaffOrderParams params) {
        Instant from = params.from() == null ? EARLIEST : params.from().atStartOfDay(zone).toInstant();
        Map<String, Long> counts = new LinkedHashMap<>();
        long all = 0;
        for (OrderStatus s : OrderStatus.values()) {
            counts.put(s.name(), 0L);
        }
        for (Object[] row : orders.countAllByStatus(from)) {
            long n = ((Number) row[1]).longValue();
            counts.put((String) row[0], n);
            all += n;
        }
        counts.put("all", all);
        counts.put("REFUND_PENDING", orders.count(filter(new StaffOrderParams("REFUND_PENDING", null, null, params.from(), null, null, null), true)));
        return counts;
    }

    /** The queue's own order: oldest first is how work is picked up; the id last so pages never overlap. */
    private static Sort sort(String sort) {
        Sort.Order first = switch (sort == null ? "newest" : sort) {
            case "oldest" -> Sort.Order.asc("placedAt");
            case "total_desc" -> Sort.Order.desc("total");
            default -> Sort.Order.desc("placedAt");
        };
        return Sort.by(first, Sort.Order.asc("id"));
    }

    private static StaffOrderRow row(Order o) {
        return new StaffOrderRow(o.getId(), o.getNumber(), o.getPlacedAt(), o.getStatus(), o.itemCount(), o.getTotal(), customer(o),
                o.getUserId() == null, o.getShippingMethodName(), o.getStatusChangedAt());
    }

    private static String customer(Order o) {
        return o.getFirstName() + " " + o.getLastName();
    }

    private static UUID actorId() {
        AuthPrincipal p = CurrentUser.principal();
        return p == null ? null : p.userId();
    }

    private static String verb(OrderStatus to) {
        return switch (to) {
            case PROCESSING -> "Started packing {target}";
            case SHIPPED -> "Marked {target} shipped";
            case DELIVERED -> "Marked {target} delivered";
            default -> "Cancelled {target}";
        };
    }

    /** "Payment successful" from PAYMENT_SUCCESSFUL, as the audit page shows a change. */
    static String label(OrderStatus status) {
        String words = status.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
