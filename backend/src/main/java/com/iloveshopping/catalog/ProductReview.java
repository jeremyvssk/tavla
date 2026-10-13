// JPA entity mapping the "product_reviews" table; one review per user per product (DB unique index).
package com.iloveshopping.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_reviews")
@Getter
@Setter
public class ProductReview {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    // The author as an id only: users belong to the user domain, and catalog never joins its table.
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String body;

    @Column(name = "verified_purchase", nullable = false)
    private boolean verifiedPurchase;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** NEW, LIVE, REPORTED or HIDDEN; see V14. Only staff moderation and a shopper's report change it. */
    @Column(nullable = false)
    private String status = "NEW";

    /** Why staff hid it; set exactly when the status is HIDDEN (ck_product_reviews_hidden_reason). */
    @Column(name = "hidden_reason")
    private String hiddenReason;

    // Changed only by the vote statements in ProductReviewRepository, as +1 and -1 in SQL, so two votes at once
    // both count; never written from here, where a stale copy would overwrite a newer count.
    @Column(name = "helpful_count", insertable = false, updatable = false)
    private int helpfulCount;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
