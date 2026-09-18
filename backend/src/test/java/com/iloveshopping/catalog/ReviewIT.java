// Integration tests for reviews: posting, duplicates, ownership on delete, and the rating trigger under concurrency.
package com.iloveshopping.catalog;

import com.iloveshopping.support.CatalogTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewIT extends CatalogTestSupport {

    @Autowired
    DataSource dataSource;

    private String admin;
    private String productId;

    @BeforeEach
    void createProduct() throws Exception {
        admin = adminToken();
        int category = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'backgammon'", Integer.class);
        productId = createProduct(admin, "Review Target " + suffix(), "for reviews", "40.00", category, null);
    }

    @Test
    void postingReviews_updatesTheAverage_andASecondReviewBySameUserConflicts() throws Exception {
        String alice = customerToken();
        String bob = customerToken();

        mockMvc.perform(json(post("/products/{id}/reviews", productId), alice, review(5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verifiedPurchase").value(false));
        mockMvc.perform(json(post("/products/{id}/reviews", productId), bob, review(2)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/products/{id}", productId))
                .andExpect(jsonPath("$.reviewCount").value(2))
                .andExpect(jsonPath("$.averageRating").value(3.50));

        mockMvc.perform(json(post("/products/{id}/reviews", productId), alice, review(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("review_already_exists"));

        mockMvc.perform(get("/products/{id}/reviews", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void postingAReview_requiresAuth_andAValidRating() throws Exception {
        mockMvc.perform(json(post("/products/{id}/reviews", productId), null, review(4)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(json(post("/products/{id}/reviews", productId), customerToken(), review(6)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.rating").exists());
        mockMvc.perform(json(post("/products/{id}/reviews", UUID.randomUUID()), customerToken(), review(4)))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyTheAuthorOrAnAdmin_canDeleteAReview() throws Exception {
        String author = customerToken();
        String stranger = customerToken();
        String reviewId = JsonPath.read(mockMvc.perform(json(post("/products/{id}/reviews", productId), author, review(4)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        // A valid token for someone else: the JWT proves identity, not ownership.
        mockMvc.perform(auth(delete("/products/{p}/reviews/{r}", productId, reviewId), stranger))
                .andExpect(status().isForbidden());
        mockMvc.perform(auth(delete("/products/{p}/reviews/{r}", productId, reviewId), author))
                .andExpect(status().isNoContent());

        String second = JsonPath.read(mockMvc.perform(json(post("/products/{id}/reviews", productId), stranger, review(1)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        mockMvc.perform(auth(delete("/products/{p}/reviews/{r}", productId, second), admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/products/{id}", productId))
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.averageRating").doesNotExist());
    }

    /**
     * Two reviews for one product, in overlapping transactions. The first holds its transaction
     * open; the second must wait on the product row lock, then recompute from a snapshot that
     * includes the first review. A trigger that skipped the separate lock step would compute the
     * second average from a snapshot taken before the wait and end with review_count = 1.
     */
    @Test
    void concurrentReviews_onOneProduct_bothCountTowardsTheAverage() throws Exception {
        UUID product = UUID.fromString(productId);
        UUID userA = UUID.fromString(jdbc.queryForObject("SELECT id::text FROM users WHERE email = ?", String.class, registerUser()));
        UUID userB = UUID.fromString(jdbc.queryForObject("SELECT id::text FROM users WHERE email = ?", String.class, registerUser()));

        try (Connection first = dataSource.getConnection()) {
            first.setAutoCommit(false);
            insertReview(first, product, userA, 5);

            CompletableFuture<Void> second = CompletableFuture.runAsync(() -> {
                try (Connection c = dataSource.getConnection()) {
                    c.setAutoCommit(false);
                    insertReview(c, product, userB, 1);
                    c.commit();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            awaitABlockedLock();
            first.commit();
            second.get(10, TimeUnit.SECONDS);
        }

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT review_count, average_rating FROM products WHERE id = ?", product);
        assertThat(row.get("review_count")).isEqualTo(2);
        assertThat((BigDecimal) row.get("average_rating")).isEqualByComparingTo("3.00");
    }

    private void awaitABlockedLock() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            Integer waiting = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'", Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("second transaction never blocked on the product row");
    }

    private static void insertReview(Connection c, UUID product, UUID user, int rating) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO product_reviews (id, product_id, user_id, rating, title, body, verified_purchase, created_at)
                VALUES (gen_random_uuid(), ?, ?, ?, 'concurrent', 'concurrent', false, now())""")) {
            ps.setObject(1, product);
            ps.setObject(2, user);
            ps.setInt(3, rating);
            ps.executeUpdate();
        }
    }

    private static String review(int rating) {
        return """
                {"rating":%d,"title":"Nice board","body":"Solid and well finished."}""".formatted(rating);
    }
}
