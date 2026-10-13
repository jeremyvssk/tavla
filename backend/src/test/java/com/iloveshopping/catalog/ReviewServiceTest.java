// Unit tests for review ownership rules: author and admin may delete, anyone else may not.
package com.iloveshopping.catalog;

import com.iloveshopping.auth.AuthPrincipal;
import com.iloveshopping.catalog.exception.CatalogNotFoundException;
import com.iloveshopping.catalog.exception.NotReviewOwnerException;
import com.iloveshopping.user.Role;
import com.iloveshopping.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    ProductReviewRepository reviewRepository;
    @Mock
    ProductRepository productRepository;
    @Mock
    PurchaseHistory purchases;
    @Mock
    UserService userService;

    ReviewService service;

    private final UUID productId = UUID.randomUUID();
    private final UUID reviewId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private ProductReview review;

    @BeforeEach
    void setUp() {
        service = new ReviewService(reviewRepository, productRepository, purchases, userService);
        review = new ProductReview();
        review.setId(reviewId);
        review.setProductId(productId);
        review.setUserId(authorId);
    }

    @Test
    void author_canDeleteTheirReview() {
        when(reviewRepository.findByIdAndProductId(reviewId, productId)).thenReturn(Optional.of(review));
        service.delete(productId, reviewId, principal(authorId, Role.CUSTOMER));
        verify(reviewRepository).delete(review);
    }

    @Test
    void anotherCustomer_cannotDeleteIt() {
        when(reviewRepository.findByIdAndProductId(reviewId, productId)).thenReturn(Optional.of(review));
        assertThatThrownBy(() -> service.delete(productId, reviewId, principal(UUID.randomUUID(), Role.CUSTOMER)))
                .isInstanceOf(NotReviewOwnerException.class);
        verify(reviewRepository, never()).delete(any(ProductReview.class));
    }

    @Test
    void anAdmin_canDeleteAnyReview() {
        when(reviewRepository.findByIdAndProductId(reviewId, productId)).thenReturn(Optional.of(review));
        service.delete(productId, reviewId, principal(UUID.randomUUID(), Role.ADMIN));
        verify(reviewRepository).delete(review);
    }

    @Test
    void aReviewUnderADifferentProduct_isNotFound() {
        when(reviewRepository.findByIdAndProductId(reviewId, productId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(productId, reviewId, principal(authorId, Role.CUSTOMER)))
                .isInstanceOf(CatalogNotFoundException.class);
    }

    private static AuthPrincipal principal(UUID userId, Role role) {
        return new AuthPrincipal(userId, role, "jti", Instant.now().plusSeconds(60), false);
    }

    @Test
    void anAuthorIsShownAsFirstNameAndInitial_neverTheWholeName() {
        assertThat(ReviewService.shortName("Toomas Rebane")).isEqualTo("Toomas R.");
        assertThat(ReviewService.shortName("Mari Liis Kask")).isEqualTo("Mari K.");
        assertThat(ReviewService.shortName("Cher")).isEqualTo("Cher");
        assertThat(ReviewService.shortName(null)).isNull();
    }

    @Test
    void theCheckFlagsContactDetailsAndLinks_asSerious() {
        assertThat(StaffReviewService.flags("Great board, buy cheaper at www.example.com"))
                .extracting(f -> f.text() + ":" + f.serious()).containsExactly("Has a link:true");
        assertThat(StaffReviewService.flags("Call me on +372 5123 4567 or write to a@b.ee about it"))
                .extracting(f -> f.text()).contains("Has a phone number", "Has an email address");
        assertThat(StaffReviewService.flags("Nice\nOk")).extracting(f -> f.text() + ":" + f.serious()).containsExactly("Very short:false");
        assertThat(StaffReviewService.flags("Solid walnut board with felted pieces, arrived well packed.")).isEmpty();
    }
}
