// Reading, posting and deleting product reviews, with the ownership check on delete.
package com.iloveshopping.catalog;

import com.iloveshopping.auth.AuthPrincipal;
import com.iloveshopping.catalog.dto.PageResponse;
import com.iloveshopping.catalog.dto.ReviewRequest;
import com.iloveshopping.catalog.dto.ReviewResponse;
import com.iloveshopping.catalog.exception.CatalogConflictException;
import com.iloveshopping.catalog.exception.CatalogNotFoundException;
import com.iloveshopping.catalog.exception.NotReviewOwnerException;
import com.iloveshopping.user.Role;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * average_rating and review_count are not touched here: the V6 trigger recomputes them inside
 * the same transaction as every insert and delete.
 */
@Service
public class ReviewService {

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;

    public ReviewService(ProductReviewRepository reviewRepository, ProductRepository productRepository) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> list(UUID productId, int page, int size) {
        requireActiveProduct(productId);
        Page<ProductReview> result = reviewRepository.findByProductId(productId,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));
        return new PageResponse<>(result.map(ReviewService::toResponse).getContent(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public ReviewResponse create(UUID productId, UUID userId, ReviewRequest request) {
        requireActiveProduct(productId);
        if (reviewRepository.existsByProductIdAndUserId(productId, userId)) {
            throw new CatalogConflictException("review_already_exists");
        }
        ProductReview review = new ProductReview();
        review.setProductId(productId);
        review.setUserId(userId);
        review.setRating(request.rating());
        review.setTitle(request.title().strip());
        review.setBody(request.body().strip());
        // No orders exist until Project 2, so nothing can be a verified purchase yet.
        review.setVerifiedPurchase(false);
        try {
            // Flush now: with a generated UUID, Hibernate would otherwise delay the INSERT to commit,
            // and the unique-index violation from a concurrent duplicate would escape this catch.
            return toResponse(reviewRepository.saveAndFlush(review));
        } catch (DataIntegrityViolationException e) {
            throw new CatalogConflictException("review_already_exists");
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

    private void requireActiveProduct(UUID productId) {
        if (!productRepository.existsByIdAndActiveTrue(productId)) {
            throw ProductService.notFound();
        }
    }

    static ReviewResponse toResponse(ProductReview r) {
        return new ReviewResponse(r.getId(), r.getProductId(), r.getRating(), r.getTitle(), r.getBody(),
                r.isVerifiedPurchase(), r.getCreatedAt());
    }
}
