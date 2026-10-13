// The staff's review moderation endpoints: the queue, and keep, hide or show again.
package com.iloveshopping.catalog;

import com.iloveshopping.catalog.dto.ModerationPageResponse;
import com.iloveshopping.catalog.dto.ModerationParams;
import com.iloveshopping.catalog.dto.ModerationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Support and admins only (SecurityConfig, StaffArea.REVIEWS). */
@RestController
@RequestMapping("/staff/reviews")
public class StaffReviewController {

    private final StaffReviewService staffReviewService;

    public StaffReviewController(StaffReviewService staffReviewService) {
        this.staffReviewService = staffReviewService;
    }

    @GetMapping
    public ModerationPageResponse list(@Valid @ModelAttribute ModerationParams params) {
        return staffReviewService.list(params);
    }

    @PostMapping("/{action}")
    public ModerationRequest.Result act(@PathVariable @Pattern(regexp = "keep|hide|unhide") String action,
                                        @Valid @RequestBody ModerationRequest request) {
        return new ModerationRequest.Result(staffReviewService.act(action, request.ids(), request.reason()));
    }
}
