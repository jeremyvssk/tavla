// The staff overview: every role gets the shop's numbers, and only the work and history of its own areas.
package com.iloveshopping.overview;

import com.iloveshopping.support.CatalogTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OverviewIT extends CatalogTestSupport {

    @Test
    void anAdminSeesEveryArea_andEveryonesRecentChanges() throws Exception {
        staff("SALES");
        mockMvc.perform(auth(get("/staff/overview"), adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesToday").isNumber())
                .andExpect(jsonPath("$.toPack").isNumber())
                .andExpect(jsonPath("$.reviewsToCheck").isNumber())
                .andExpect(jsonPath("$.lowStock").isNumber())
                .andExpect(jsonPath("$.staffWithout2fa").isNumber())
                .andExpect(jsonPath("$.recent[*].actor").isNotEmpty());
    }

    @Test
    void salesGetsTheCatalogsWork_notTheOrdersOrReviews_andOnlyTheirOwnChanges() throws Exception {
        Staff sales = staff("SALES");
        String body = mockMvc.perform(auth(get("/staff/overview"), sales.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesToday").isNumber())
                .andExpect(jsonPath("$.lowStock").isNumber())
                .andExpect(jsonPath("$.toPack").isEmpty())
                .andExpect(jsonPath("$.reviewsToCheck").isEmpty())
                .andExpect(jsonPath("$.staffWithout2fa").isEmpty())
                .andExpect(jsonPath("$.latestOrders.length()").value(0))
                .andReturn().getResponse().getContentAsString();
        List<String> roles = JsonPath.read(body, "$.recent[*].actorRole");
        assertThat(roles).isNotEmpty().containsOnly("SALES");
    }

    @Test
    void supportGetsOrdersAndReviews_notTheCatalog() throws Exception {
        mockMvc.perform(auth(get("/staff/overview"), staff("SUPPORT").token()))
                .andExpect(jsonPath("$.toPack").isNumber())
                .andExpect(jsonPath("$.reviewsToCheck").isNumber())
                .andExpect(jsonPath("$.lowStock").isEmpty());
        mockMvc.perform(auth(get("/staff/overview"), customerToken())).andExpect(status().isForbidden());
    }
}
