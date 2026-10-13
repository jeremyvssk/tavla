// GET /staff/overview: the first page every staff member sees.
package com.iloveshopping.overview;

import com.iloveshopping.auth.AuthPrincipal;
import com.iloveshopping.overview.dto.OverviewResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Every staff role (StaffArea.OVERVIEW); what each one is shown is narrowed in OverviewService. */
@RestController
public class OverviewController {

    private final OverviewService overviewService;

    public OverviewController(OverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @GetMapping("/staff/overview")
    public OverviewResponse overview(@AuthenticationPrincipal AuthPrincipal caller) {
        return overviewService.overview(caller);
    }
}
