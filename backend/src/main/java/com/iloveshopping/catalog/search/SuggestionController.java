// Public search-as-you-type endpoint.
package com.iloveshopping.catalog.search;

import com.iloveshopping.catalog.dto.Suggestion;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/search")
public class SuggestionController {

    private final SearchService searchService;

    public SuggestionController(SearchService searchService) {
        this.searchService = searchService;
    }

    /** Fewer than two characters returns an empty list rather than most of the catalog. */
    @GetMapping("/suggestions")
    public List<Suggestion> suggestions(@RequestParam(defaultValue = "") @Size(max = 100) String q) {
        return searchService.suggest(q);
    }
}
