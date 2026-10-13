// Spring Data JPA repository for product reviews.
package com.iloveshopping.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductReviewRepository extends JpaRepository<ProductReview, UUID>, JpaSpecificationExecutor<ProductReview> {

    Page<ProductReview> findByProductId(UUID productId, Pageable pageable);

    Optional<ProductReview> findByProductIdAndUserId(UUID productId, UUID userId);

    List<ProductReview> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Rows of (rating, count) over the product's shown reviews. */
    @Query(value = "SELECT rating, count(*) FROM product_reviews WHERE product_id = :productId AND status <> 'HIDDEN' GROUP BY rating",
            nativeQuery = true)
    List<Object[]> distribution(UUID productId);

    /** 1 when the vote is new; 0 when this shopper had already voted for it. */
    @Modifying
    @Query(value = "INSERT INTO review_votes (review_id, user_id) VALUES (:reviewId, :userId) ON CONFLICT DO NOTHING", nativeQuery = true)
    int addVote(UUID reviewId, UUID userId);

    @Modifying
    @Query(value = "DELETE FROM review_votes WHERE review_id = :reviewId AND user_id = :userId", nativeQuery = true)
    int removeVote(UUID reviewId, UUID userId);

    /** +1 or -1 in one statement: the row lock makes two votes at once queue, and both count. */
    @Modifying
    @Query(value = "UPDATE product_reviews SET helpful_count = helpful_count + :by WHERE id = :reviewId", nativeQuery = true)
    int addHelpful(UUID reviewId, int by);

    /** Takes back every helpful vote the account gave, before the account (and with it the vote rows) goes. */
    @Modifying
    @Query(value = """
            UPDATE product_reviews SET helpful_count = helpful_count - 1
            WHERE id IN (SELECT review_id FROM review_votes WHERE user_id = :userId)""", nativeQuery = true)
    int forgetVotesOf(UUID userId);

    @Query(value = "SELECT helpful_count FROM product_reviews WHERE id = :reviewId", nativeQuery = true)
    int helpfulCount(UUID reviewId);

    @Query(value = "SELECT review_id FROM review_votes WHERE user_id = :userId AND review_id IN (:reviewIds)", nativeQuery = true)
    List<UUID> votedBy(UUID userId, Collection<UUID> reviewIds);

    /** 1 for a new report; 0 when this shopper already has an open report on the review. */
    @Modifying
    @Query(value = """
            INSERT INTO review_reports (review_id, user_id, reason) VALUES (:reviewId, :userId, :reason)
            ON CONFLICT (review_id, user_id) WHERE resolved_at IS NULL DO NOTHING""", nativeQuery = true)
    int addReport(UUID reviewId, UUID userId, String reason);

    /** A shown review goes to the staff's queue; a hidden one stays hidden. */
    @Modifying
    @Query(value = "UPDATE product_reviews SET status = 'REPORTED' WHERE id = :reviewId AND status IN ('NEW', 'LIVE')", nativeQuery = true)
    int markReported(UUID reviewId);

    @Modifying
    @Query(value = "UPDATE review_reports SET resolved_at = now() WHERE review_id IN (:reviewIds) AND resolved_at IS NULL", nativeQuery = true)
    int resolveReports(Collection<UUID> reviewIds);

    /** Rows of (review id, reason, created_at) for the open reports, newest first. */
    @Query(value = """
            SELECT review_id, reason, created_at FROM review_reports
            WHERE review_id IN (:reviewIds) AND resolved_at IS NULL ORDER BY created_at DESC""", nativeQuery = true)
    List<Object[]> openReports(Collection<UUID> reviewIds);

    /** Rows of (status, count) over every review, for the moderation tabs. */
    @Query(value = "SELECT status, count(*) FROM product_reviews GROUP BY status", nativeQuery = true)
    List<Object[]> countByStatus();

    Optional<ProductReview> findByIdAndProductId(UUID id, UUID productId);

    boolean existsByProductIdAndUserId(UUID productId, UUID userId);
}
