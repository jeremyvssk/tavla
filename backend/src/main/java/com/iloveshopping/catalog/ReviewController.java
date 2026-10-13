// Review endpoints: public list and summary; signed-in post, helpful vote and report; owner-or-admin delete.
package com.iloveshopping.catalog;

import com.iloveshopping.auth.AuthPrincipal;
import com.iloveshopping.catalog.dto.PageResponse;
import com.iloveshopping.catalog.dto.ReviewListParams;
import com.iloveshopping.catalog.dto.ReviewReportRequest;
import com.iloveshopping.catalog.dto.ReviewRequest;
import com.iloveshopping.catalog.dto.ReviewResponse;
import com.iloveshopping.catalog.dto.ReviewSummaryResponse;
import com.iloveshopping.catalog.dto.ReviewVoteRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The list and summary are public; a token, when sent, adds the shopper's own votes and review to them. */
@RestController
@RequestMapping("/products/{productId}/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public PageResponse<ReviewResponse> list(@PathVariable UUID productId, @Valid @ModelAttribute ReviewListParams params) {
        return reviewService.list(productId, params);
    }

    @GetMapping("/summary")
    public ReviewSummaryResponse summary(@PathVariable UUID productId) {
        return reviewService.summary(productId);
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> create(@PathVariable UUID productId,
                                                 @AuthenticationPrincipal AuthPrincipal principal,
                                                 @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(productId, principal.userId(), request));
    }

    @PutMapping("/{reviewId}/vote")
    public ReviewResponse vote(@PathVariable UUID productId, @PathVariable UUID reviewId,
                               @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody ReviewVoteRequest request) {
        return reviewService.vote(productId, reviewId, principal.userId(), true);
    }

    @DeleteMapping("/{reviewId}/vote")
    public ReviewResponse unvote(@PathVariable UUID productId, @PathVariable UUID reviewId,
                                 @AuthenticationPrincipal AuthPrincipal principal) {
        return reviewService.vote(productId, reviewId, principal.userId(), false);
    }

    @PostMapping("/{reviewId}/report")
    public ResponseEntity<Void> report(@PathVariable UUID productId, @PathVariable UUID reviewId,
                                       @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody ReviewReportRequest request) {
        reviewService.report(productId, reviewId, principal.userId(), request.reason());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> delete(@PathVariable UUID productId, @PathVariable UUID reviewId,
                                       @AuthenticationPrincipal AuthPrincipal principal) {
        reviewService.delete(productId, reviewId, principal);
        return ResponseEntity.noContent().build();
    }
}
