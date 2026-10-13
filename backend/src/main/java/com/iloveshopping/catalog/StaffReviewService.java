// Review moderation: the queue of new and reported reviews, what the automatic check noticed, and keep, hide or show again.
package com.iloveshopping.catalog;

import com.iloveshopping.audit.AuditEvent;
import com.iloveshopping.audit.AuditKind;
import com.iloveshopping.audit.AuditLog;
import com.iloveshopping.audit.AuditTarget;
import com.iloveshopping.catalog.dto.ModerationPageResponse;
import com.iloveshopping.catalog.dto.ModerationParams;
import com.iloveshopping.catalog.dto.ModerationReviewResponse;
import com.iloveshopping.exception.FieldValidationException;
import com.iloveshopping.user.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Moderation after the fact: a shopper's review is shown as soon as it is posted and waits here as NEW.
 * Keeping it (LIVE) clears it and its reports from the queue; hiding it takes it off the site and out of
 * the average, with a reason its author is shown; showing it again undoes that. Every decision is audited
 * per review, so "who hid this" has an answer.
 */
@Service
public class StaffReviewService {

    private static final Pattern LINK = Pattern.compile("(?i)(https?://|www\\.|\\b[a-z0-9-]+\\.(com|ee|net|org|io|ru|lv|lt|fi)\\b)");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d\\s-]{6,}\\d");

    private final ProductReviewRepository reviews;
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final PurchaseHistory purchases;
    private final UserService userService;
    private final AuditLog auditLog;

    public StaffReviewService(ProductReviewRepository reviews, ProductRepository products, ProductImageRepository images,
                              PurchaseHistory purchases, UserService userService, AuditLog auditLog) {
        this.reviews = reviews;
        this.products = products;
        this.images = images;
        this.purchases = purchases;
        this.userService = userService;
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public ModerationPageResponse list(ModerationParams params) {
        int page = params.page() == null ? 0 : params.page();
        int size = params.size() == null ? 25 : params.size();
        // the queue is worked oldest first; the other tabs read newest first unless asked
        String state = params.state() == null ? "todo" : params.state();
        String sort = params.sort() != null ? params.sort() : state.equals("todo") || state.equals("reported") ? "oldest" : "newest";
        Sort order = sort.equals("oldest") ? Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))
                : Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
        Page<ProductReview> found = reviews.findAll(filter(state, params.rating()), PageRequest.of(page, size, order));
        return new ModerationPageResponse(views(found.getContent()), page, size, found.getTotalElements(), found.getTotalPages(), counts());
    }

    /** keep, hide or unhide; the reviews already in the asked-for state are left alone and not counted. */
    @Transactional
    public int act(String action, List<UUID> ids, String reason) {
        if (action.equals("hide") && (reason == null || reason.isBlank())) {
            throw new FieldValidationException("reason", "Say why it is hidden; its author is shown the reason.");
        }
        List<ProductReview> found = reviews.findAllById(new LinkedHashSet<>(ids));
        Map<UUID, String> productNames = products.findAllById(found.stream().map(ProductReview::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getId, Product::getName));
        int done = 0;
        List<UUID> cleared = new ArrayList<>();
        for (ProductReview r : found) {
            String before = r.getStatus();
            switch (action) {
                case "keep" -> {
                    if (!before.equals("NEW") && !before.equals("REPORTED")) {
                        continue;
                    }
                    r.setStatus("LIVE");
                }
                case "hide" -> {
                    if (before.equals("HIDDEN")) {
                        continue;
                    }
                    r.setStatus("HIDDEN");
                    r.setHiddenReason(reason.strip());
                }
                default -> {
                    if (!before.equals("HIDDEN")) {
                        continue;
                    }
                    r.setStatus("LIVE");
                    r.setHiddenReason(null);
                }
            }
            cleared.add(r.getId());
            done++;
            AuditEvent event = AuditEvent.of(AuditKind.REVIEWS, switch (action) {
                case "keep" -> "Kept {target}";
                case "hide" -> "Hid {target}";
                default -> "Showed {target} again";
            }).target(AuditTarget.REVIEW, r.getId(), "a review of " + productNames.getOrDefault(r.getProductId(), "a product"))
                    .change(label(before), label(r.getStatus()));
            auditLog.record(action.equals("hide") ? event.detail(reason.strip()) : event);
        }
        reviews.saveAllAndFlush(found);
        if (!cleared.isEmpty() && !action.equals("unhide")) {
            // a decision answers the reports made so far; a later report opens a new one
            reviews.resolveReports(cleared);
        }
        return done;
    }

    private static Specification<ProductReview> filter(String state, String rating) {
        Specification<ProductReview> spec = switch (state) {
            case "reported" -> (root, query, cb) -> cb.equal(root.get("status"), "REPORTED");
            case "live" -> (root, query, cb) -> cb.equal(root.get("status"), "LIVE");
            case "hidden" -> (root, query, cb) -> cb.equal(root.get("status"), "HIDDEN");
            default -> (root, query, cb) -> root.get("status").in("NEW", "REPORTED");
        };
        if (rating != null) {
            int[] band = switch (rating) {
                case "low" -> new int[] {1, 2};
                case "mid" -> new int[] {3, 3};
                default -> new int[] {4, 5};
            };
            spec = spec.and((root, query, cb) -> cb.between(root.get("rating"), band[0], band[1]));
        }
        return spec;
    }

    /** todo, reported, live and hidden: the moderation tabs, and the overview's review counts. */
    @Transactional(readOnly = true)
    public Map<String, Long> counts() {
        Map<String, Long> byStatus = new HashMap<>();
        for (Object[] row : reviews.countByStatus()) {
            byStatus.put((String) row[0], ((Number) row[1]).longValue());
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("todo", byStatus.getOrDefault("NEW", 0L) + byStatus.getOrDefault("REPORTED", 0L));
        counts.put("reported", byStatus.getOrDefault("REPORTED", 0L));
        counts.put("live", byStatus.getOrDefault("LIVE", 0L));
        counts.put("hidden", byStatus.getOrDefault("HIDDEN", 0L));
        return counts;
    }

    private List<ModerationReviewResponse> views(List<ProductReview> page) {
        if (page.isEmpty()) {
            return List.of();
        }
        List<UUID> productIds = page.stream().map(ProductReview::getProductId).distinct().toList();
        Map<UUID, Product> byId = products.findAllById(productIds).stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<UUID, ProductImage> firstImage = new HashMap<>();
        images.findByProductIdInOrderByPrimaryDescDisplayOrderAscIdAsc(productIds).forEach(i -> firstImage.putIfAbsent(i.getProductId(), i));
        Map<UUID, String> names = userService.namesOf(page.stream().map(ProductReview::getUserId).toList());
        Map<UUID, List<ModerationReviewResponse.Report>> reports = new HashMap<>();
        for (Object[] row : reviews.openReports(page.stream().map(ProductReview::getId).toList())) {
            reports.computeIfAbsent((UUID) row[0], k -> new ArrayList<>()).add(new ModerationReviewResponse.Report((String) row[1], instant(row[2])));
        }
        List<ModerationReviewResponse> out = new ArrayList<>();
        for (ProductReview r : page) {
            Product p = byId.get(r.getProductId());
            ProductImage image = firstImage.get(r.getProductId());
            String order = purchases.delivered(r.getUserId(), r.getProductId()).map(PurchaseHistory.Purchase::orderNumber).orElse(null);
            out.add(new ModerationReviewResponse(r.getId(), r.getRating(), r.getTitle(), r.getBody(), r.isVerifiedPurchase(),
                    r.getCreatedAt(), ReviewService.shortName(names.get(r.getUserId())), r.getHelpfulCount(), r.getProductId(),
                    p == null ? null : p.getName(), image == null ? null : image.getUrl(), image == null ? null : image.getFraming(),
                    order, flags(r.getTitle() + "\n" + r.getBody()), reports.getOrDefault(r.getId(), List.of()), r.getStatus(),
                    r.getHiddenReason()));
        }
        return out;
    }

    /**
     * What a person should look at first. Contact details and links are serious: reviews are a common place
     * for spam and for people leaving a phone number they will regret. A very short one is only a note.
     */
    static List<ModerationReviewResponse.Flag> flags(String text) {
        List<ModerationReviewResponse.Flag> flags = new ArrayList<>();
        if (LINK.matcher(text).find()) {
            flags.add(new ModerationReviewResponse.Flag("Has a link", true));
        }
        if (EMAIL.matcher(text).find()) {
            flags.add(new ModerationReviewResponse.Flag("Has an email address", true));
        }
        if (PHONE.matcher(text).find()) {
            flags.add(new ModerationReviewResponse.Flag("Has a phone number", true));
        }
        if (text.strip().length() < 30) {
            flags.add(new ModerationReviewResponse.Flag("Very short", false));
        }
        return flags;
    }

    private static String label(String status) {
        return status.charAt(0) + status.substring(1).toLowerCase();
    }

    /** A native TIMESTAMP column comes back as whatever the driver picks; times are stored in UTC (hibernate.jdbc.time_zone). */
    private static Instant instant(Object value) {
        return switch (value) {
            case Timestamp t -> t.toInstant();
            case LocalDateTime t -> t.toInstant(ZoneOffset.UTC);
            case Instant t -> t;
            default -> throw new IllegalStateException("unexpected timestamp type " + value.getClass());
        };
    }
}
