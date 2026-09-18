// Spring Data JPA repository for product reviews.
package com.iloveshopping.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductReviewRepository extends JpaRepository<ProductReview, UUID> {

    Page<ProductReview> findByProductId(UUID productId, Pageable pageable);

    Optional<ProductReview> findByIdAndProductId(UUID id, UUID productId);

    boolean existsByProductIdAndUserId(UUID productId, UUID userId);
}
