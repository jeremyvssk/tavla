// Reading, posting and deleting product reviews, helpful votes and reports, with the ownership and purchase checks.
package com.iloveshopping.catalog;

import com.iloveshopping.auth.AuthPrincipal;
import com.iloveshopping.auth.CurrentUser;
import com.iloveshopping.catalog.dto.PageResponse;
import com.iloveshopping.catalog.dto.ReviewListParams;
import com.iloveshopping.catalog.dto.ReviewRequest;
import com.iloveshopping.catalog.dto.ReviewResponse;
import com.iloveshopping.catalog.dto.ReviewSummaryResponse;
import com.iloveshopping.catalog.exception.CatalogConflictException;
import com.iloveshopping.catalog.exception.CatalogNotFoundException;
import com.iloveshopping.catalog.exception.NotReviewOwnerException;
import com.iloveshopping.catalog.exception.ReviewNotAllowedException;
import com.iloveshopping.user.Role;
import com.iloveshopping.user.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * average_rating and review_count are not touched here: the V6/V14 trigger recomputes them, leaving hidden
 * reviews out, inside the same transaction as every insert, delete and status change.
 *
 * <p>Only an account that received the product (a delivered order, asked of the order domain through
 * {@link PurchaseHistory}) may review it, so every review is a verified purchase. A new review is shown at
 * once and waits in the staff's queue (status NEW); staff keep or hide it afterwards. A hidden review stays
 * in the table: its author still sees it, marked as not published, with the reason.
 */
@Service
public class ReviewService {

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final PurchaseHistory purchases;
    private final UserService userService;

    public ReviewService(ProductReviewRepository reviewRepository, ProductRepository productRepository, PurchaseHistory purchases,
                         UserService userService) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.purchases = purchases;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> list(UUID productId, ReviewListParams params) {
        requireActiveProduct(productId);
        int page = params.page() == null ? 0 : params.page();
        int size = params.size() == null ? 10 : params.size();
        Specification<ProductReview> spec = (root, query, cb) -> cb.and(cb.equal(root.get("productId"), productId),
                cb.notEqual(root.get("status"), "HIDDEN"));
        if (params.rating() != null) {
            int stars = params.rating();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("rating"), stars));
        }
        Page<ProductReview> result = reviewRepository.findAll(spec, PageRequest.of(page, size, sort(params.sort())));
        return new PageResponse<>(views(result.getContent()), page, size, result.getTotalElements(), result.getTotalPages());
    }

    /** The star spread, and for a signed-in shopper whether they may review and their own review, hidden or not. */
    @Transactional(readOnly = true)
    public ReviewSummaryResponse summary(UUID productId) {
        Product product = productRepository.findByIdAndActiveTrue(productId).orElseThrow(ProductService::notFound);
        Integer[] spread = {0, 0, 0, 0, 0};
        for (Object[] row : reviewRepository.distribution(productId)) {
            spread[((Number) row[0]).intValue() - 1] = ((Number) row[1]).intValue();
        }
        AuthPrincipal caller = CurrentUser.principal();
        ReviewSummaryResponse.Me me = null;
        if (caller != null) {
            Optional<ProductReview> own = reviewRepository.findByProductIdAndUserId(productId, caller.userId());
            me = new ReviewSummaryResponse.Me(
                    purchases.delivered(caller.userId(), productId).map(PurchaseHistory.Purchase::placedAt).orElse(null),
                    own.isPresent(), own.map(r -> views(List.of(r)).getFirst()).orElse(null),
                    own.map(ProductReview::getHiddenReason).orElse(null));
        }
        BigDecimal average = product.getAverageRating() == null ? null : product.getAverageRating().setScale(2, RoundingMode.HALF_UP);
        return new ReviewSummaryResponse(average, product.getReviewCount() == null ? 0 : product.getReviewCount(), List.of(spread), me);
    }

    /**
     * Before an account is deleted: its vote rows go with it (ON DELETE CASCADE), so the counts they added are
     * taken back first. In the deletion's own transaction, so either both happen or neither.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void forgetVotesOf(UUID userId) {
        reviewRepository.forgetVotesOf(userId);
    }

    /** Every review the account wrote, hidden ones too: the account's data export. */
    @Transactional(readOnly = true)
    public List<ReviewResponse> allBy(UUID userId) {
        return views(reviewRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    @Transactional
    public ReviewResponse create(UUID productId, UUID userId, ReviewRequest request) {
        requireActiveProduct(productId);
        if (purchases.delivered(userId, productId).isEmpty()) {
            throw new ReviewNotAllowedException("review_needs_delivery");
        }
        if (reviewRepository.existsByProductIdAndUserId(productId, userId)) {
            throw new CatalogConflictException("review_already_exists");
        }
        ProductReview review = new ProductReview();
        review.setProductId(productId);
        review.setUserId(userId);
        review.setRating(request.rating());
        review.setTitle(request.title().strip());
        review.setBody(request.body().strip());
        review.setVerifiedPurchase(true);
        review.setStatus("NEW");
        try {
            // Flush now: with a generated UUID, Hibernate would otherwise delay the INSERT to commit,
            // and the unique-index violation from a concurrent duplicate would escape this catch.
            return views(List.of(reviewRepository.saveAndFlush(review))).getFirst();
        } catch (DataIntegrityViolationException e) {
            throw new CatalogConflictException("review_already_exists");
        }
    }

    /**
     * One helpful vote per shopper: the insert does nothing when the vote is already there, so pressing twice,
     * or two tabs at once, counts once. The count moves by one in SQL in the same transaction as the vote row.
     */
    @Transactional
    public ReviewResponse vote(UUID productId, UUID reviewId, UUID userId, boolean helpful) {
        ProductReview review = shown(productId, reviewId);
        if (review.getUserId().equals(userId)) {
            throw new ReviewNotAllowedException("own_review");
        }
        int changed = helpful ? reviewRepository.addVote(reviewId, userId) : reviewRepository.removeVote(reviewId, userId);
        if (changed == 1) {
            reviewRepository.addHelpful(reviewId, helpful ? 1 : -1);
        }
        // the count from the table: helpful_count is never written through the entity, so the loaded copy is the old one
        ReviewResponse v = views(List.of(review)).getFirst();
        return new ReviewResponse(v.id(), v.productId(), v.rating(), v.title(), v.body(), v.verifiedPurchase(), v.createdAt(),
                v.authorName(), reviewRepository.helpfulCount(reviewId), v.myVote(), v.published());
    }

    /** Puts a shown review in the staff's queue. A second report by the same shopper adds nothing. */
    @Transactional
    public void report(UUID productId, UUID reviewId, UUID userId, String reason) {
        ProductReview review = shown(productId, reviewId);
        if (review.getUserId().equals(userId)) {
            throw new ReviewNotAllowedException("own_review");
        }
        if (reviewRepository.addReport(reviewId, userId, reason.strip()) == 1) {
            reviewRepository.markReported(reviewId);
        }
    }

    /** The token proves who is asking; the stored user_id decides whether they may delete. */
    @Transactional
    public void delete(UUID productId, UUID reviewId, AuthPrincipal caller) {
        ProductReview review = reviewRepository.findByIdAndProductId(reviewId, productId)
                .orElseThrow(() -> new CatalogNotFoundException("review_not_found"));
        if (!review.getUserId().equals(caller.userId()) && caller.role() != Role.ADMIN) {
            throw new NotReviewOwnerException();
        }
        reviewRepository.delete(review);
    }

    /** A review shoppers can see: on this product, and not hidden. */
    private ProductReview shown(UUID productId, UUID reviewId) {
        return reviewRepository.findByIdAndProductId(reviewId, productId).filter(r -> !"HIDDEN".equals(r.getStatus()))
                .orElseThrow(() -> new CatalogNotFoundException("review_not_found"));
    }

    private void requireActiveProduct(UUID productId) {
        if (!productRepository.existsByIdAndActiveTrue(productId)) {
            throw ProductService.notFound();
        }
    }

    /** Helpful first is the default: the reviews other shoppers found useful, then the newest. From an allowlist. */
    private static Sort sort(String sort) {
        return switch (sort == null ? "helpful" : sort) {
            case "newest" -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
            case "highest" -> Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
            case "lowest" -> Sort.by(Sort.Order.asc("rating"), Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
            default -> Sort.by(Sort.Order.desc("helpfulCount"), Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
        };
    }

    /** With author names and the caller's votes, looked up once for the whole page. */
    private List<ReviewResponse> views(List<ProductReview> reviews) {
        Map<UUID, String> names = userService.namesOf(reviews.stream().map(ProductReview::getUserId).toList());
        AuthPrincipal caller = CurrentUser.principal();
        Set<UUID> voted = caller == null || reviews.isEmpty() ? Set.of()
                : new HashSet<>(reviewRepository.votedBy(caller.userId(), reviews.stream().map(ProductReview::getId).toList()));
        List<ReviewResponse> out = new ArrayList<>();
        for (ProductReview r : reviews) {
            out.add(new ReviewResponse(r.getId(), r.getProductId(), r.getRating(), r.getTitle(), r.getBody(), r.isVerifiedPurchase(),
                    r.getCreatedAt(), shortName(names.get(r.getUserId())), r.getHelpfulCount(), voted.contains(r.getId()) ? "helpful" : null,
                    !"HIDDEN".equals(r.getStatus())));
        }
        return out;
    }

    /** "Toomas R." from "Toomas Rebane"; a deleted account's review shows no name. */
    static String shortName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String[] parts = fullName.strip().split("\\s+");
        return parts.length == 1 ? parts[0] : parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }
}
