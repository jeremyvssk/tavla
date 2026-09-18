// Review endpoints: public list, authenticated post, owner-or-admin delete.
package com.iloveshopping.catalog;

import com.iloveshopping.auth.AuthPrincipal;
import com.iloveshopping.catalog.dto.PageResponse;
import com.iloveshopping.catalog.dto.ReviewRequest;
import com.iloveshopping.catalog.dto.ReviewResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/products/{productId}/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public PageResponse<ReviewResponse> list(@PathVariable UUID productId,
                                             @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
                                             @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return reviewService.list(productId, page, size);
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> create(@PathVariable UUID productId,
                                                 @AuthenticationPrincipal AuthPrincipal principal,
                                                 @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(productId, principal.userId(), request));
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> delete(@PathVariable UUID productId, @PathVariable UUID reviewId,
                                       @AuthenticationPrincipal AuthPrincipal principal) {
        reviewService.delete(productId, reviewId, principal);
        return ResponseEntity.noContent().build();
    }
}
