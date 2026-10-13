// Helpful votes, shoppers' reports and staff moderation: the queue, keep, hide with a reason the author sees, and show again.
package com.iloveshopping.catalog;

import com.iloveshopping.support.CatalogTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewModerationIT extends CatalogTestSupport {

    private String productId;
    private String author;
    private String reviewId;

    @BeforeEach
    void aReview() throws Exception {
        int category = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'backgammon'", Integer.class);
        productId = createProduct(adminToken(), "Moderated " + suffix(), "x", "40.00", category, null);
        author = buyerOf(productId);
        reviewId = write(author, 2, "Loose hinge", "The hinge was loose after a week of play, sadly.");
    }

    @Test
    void aNewReviewIsShownAtOnce_andWaitsInTheStaffQueue() throws Exception {
        mockMvc.perform(get("/products/{id}/reviews", productId)).andExpect(jsonPath("$.items[0].id").value(reviewId));
        assertThat(state()).isEqualTo("NEW");
        mockMvc.perform(auth(get("/staff/reviews"), staff("SUPPORT").token()).param("size", "100").param("sort", "newest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(hasItem(reviewId)))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].orderNumber".formatted(reviewId)).isNotEmpty())
                .andExpect(jsonPath("$.counts.todo").isNumber());
    }

    @Test
    void aHelpfulVoteCountsOncePerShopper_canBeTakenBack_andNotOnOnesOwnReview() throws Exception {
        String voter = customerToken();
        vote(voter).andExpect(status().isOk()).andExpect(jsonPath("$.helpfulCount").value(1)).andExpect(jsonPath("$.myVote").value("helpful"));
        vote(voter).andExpect(jsonPath("$.helpfulCount").value(1));
        mockMvc.perform(auth(get("/products/{id}/reviews", productId), voter)).andExpect(jsonPath("$.items[0].myVote").value("helpful"));
        mockMvc.perform(auth(delete("/products/{p}/reviews/{r}/vote", productId, reviewId), voter))
                .andExpect(jsonPath("$.helpfulCount").value(0)).andExpect(jsonPath("$.myVote").isEmpty());
        vote(author).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("own_review"));
        vote(null).andExpect(status().isUnauthorized());
        mockMvc.perform(json(put("/products/{p}/reviews/{r}/vote", productId, reviewId), voter, "{\"vote\":\"not_helpful\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void manyVotesAtOnceAllCount() throws Exception {
        List<String> voters = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            voters.add(customerToken());
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Integer>> calls = voters.stream().<Callable<Integer>>map(t -> () -> vote(t).andReturn().getResponse().getStatus()).toList();
            for (Future<Integer> f : pool.invokeAll(calls)) {
                assertThat(f.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdown();
        }
        assertThat(jdbc.queryForObject("SELECT helpful_count FROM product_reviews WHERE id = ?::uuid", Integer.class, reviewId)).isEqualTo(8);
    }

    @Test
    void aReportPutsItOnTheReportedTab_andKeepingClearsIt() throws Exception {
        String shopper = customerToken();
        report(shopper, "Rude about the seller").andExpect(status().isNoContent());
        report(shopper, "Again").andExpect(status().isNoContent());
        assertThat(state()).isEqualTo("REPORTED");
        String support = staff("SUPPORT").token();
        mockMvc.perform(auth(get("/staff/reviews"), support).param("state", "reported").param("size", "100"))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].reports.length()".formatted(reviewId)).value(1))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].reports[0].reason".formatted(reviewId)).value("Rude about the seller"));

        act(support, "keep", null).andExpect(status().isOk()).andExpect(jsonPath("$.done").value(1));
        assertThat(state()).isEqualTo("LIVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM review_reports WHERE review_id = ?::uuid AND resolved_at IS NULL",
                Integer.class, reviewId)).isZero();
        act(support, "keep", null).andExpect(jsonPath("$.done").value(0));
        report(author, "Mine").andExpect(status().isForbidden());
    }

    @Test
    void aHiddenReviewLeavesTheShopAndTheAverage_butItsAuthorSeesItWithTheReason() throws Exception {
        String other = write(buyerOf(productId), 4, "Good", "Solid board, nice felt, would buy it again for the club.");
        mockMvc.perform(get("/products/{id}", productId)).andExpect(jsonPath("$.averageRating").value(3.00));
        String support = staff("SUPPORT").token();

        act(support, "hide", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.reason").exists());
        act(support, "hide", "Shares a phone number").andExpect(jsonPath("$.done").value(1));

        mockMvc.perform(get("/products/{id}/reviews", productId))
                .andExpect(jsonPath("$.items[*].id").value(not(hasItem(reviewId))))
                .andExpect(jsonPath("$.items[*].id").value(hasItem(other)));
        mockMvc.perform(get("/products/{id}", productId))
                .andExpect(jsonPath("$.averageRating").value(4.00)).andExpect(jsonPath("$.reviewCount").value(1));
        mockMvc.perform(auth(get("/products/{id}/reviews/summary", productId), author))
                .andExpect(jsonPath("$.me.reviewed").value(true))
                .andExpect(jsonPath("$.me.review.published").value(false))
                .andExpect(jsonPath("$.me.hiddenReason").value("Shares a phone number"))
                .andExpect(jsonPath("$.distribution[1]").value(0))
                .andExpect(jsonPath("$.distribution[3]").value(1));
        vote(customerToken()).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_id = ? AND action = 'Hid {target}'",
                Integer.class, reviewId)).isEqualTo(1);

        act(support, "unhide", null).andExpect(jsonPath("$.done").value(1));
        mockMvc.perform(get("/products/{id}", productId)).andExpect(jsonPath("$.averageRating").value(3.00));
    }

    @Test
    void theSummaryTellsAShopperWhetherTheyMayReview() throws Exception {
        mockMvc.perform(get("/products/{id}/reviews/summary", productId)).andExpect(jsonPath("$.me").isEmpty());
        mockMvc.perform(auth(get("/products/{id}/reviews/summary", productId), customerToken()))
                .andExpect(jsonPath("$.me.purchasedAt").isEmpty())
                .andExpect(jsonPath("$.me.reviewed").value(false));
        mockMvc.perform(auth(get("/products/{id}/reviews/summary", productId), buyerOf(productId)))
                .andExpect(jsonPath("$.me.purchasedAt").isNotEmpty());
    }

    @Test
    void theMostHelpfulComeFirst() throws Exception {
        String other = write(buyerOf(productId), 5, "Lovely", "The best board in the club, everyone wants to play on it.");
        vote(customerToken());
        mockMvc.perform(get("/products/{id}/reviews", productId)).andExpect(jsonPath("$.items[0].id").value(reviewId));
        mockMvc.perform(get("/products/{id}/reviews", productId).param("sort", "highest")).andExpect(jsonPath("$.items[0].id").value(other));
        mockMvc.perform(get("/products/{id}/reviews", productId).param("rating", "5")).andExpect(jsonPath("$.totalItems").value(1));
        mockMvc.perform(get("/products/{id}/reviews", productId).param("sort", "random")).andExpect(status().isBadRequest());
    }

    @Test
    void salesHasNoReviewDesk() throws Exception {
        mockMvc.perform(auth(get("/staff/reviews"), staff("SALES").token())).andExpect(status().isForbidden());
        act(adminToken(), "delete", null).andExpect(status().isBadRequest());
    }

    private String write(String token, int rating, String title, String body) throws Exception {
        return JsonPath.read(mockMvc.perform(json(post("/products/{id}/reviews", productId), token, """
                        {"rating":%d,"title":"%s","body":"%s"}""".formatted(rating, title, body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions vote(String token) throws Exception {
        var request = put("/products/{p}/reviews/{r}/vote", productId, reviewId);
        return mockMvc.perform(token == null ? request.contentType("application/json").content("{\"vote\":\"helpful\"}")
                : json(request, token, "{\"vote\":\"helpful\"}"));
    }

    private ResultActions report(String token, String reason) throws Exception {
        return mockMvc.perform(json(post("/products/{p}/reviews/{r}/report", productId, reviewId), token, "{\"reason\":\"%s\"}".formatted(reason)));
    }

    private ResultActions act(String token, String action, String reason) throws Exception {
        return mockMvc.perform(json(post("/staff/reviews/{a}", action), token,
                "{\"ids\":[\"%s\"],\"reason\":%s}".formatted(reviewId, reason == null ? "null" : "\"" + reason + "\"")));
    }

    private String state() {
        return jdbc.queryForObject("SELECT status FROM product_reviews WHERE id = ?::uuid", String.class, reviewId);
    }
}
