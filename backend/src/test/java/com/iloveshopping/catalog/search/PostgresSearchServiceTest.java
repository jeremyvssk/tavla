// Unit tests for the search service's pure helpers: sort fallback and LIKE escaping.
package com.iloveshopping.catalog.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresSearchServiceTest {

    @Test
    void relevance_fallsBackToNewest_whenThereIsNothingToRank() {
        assertThat(PostgresSearchService.resolveSort(null, true)).isEqualTo("relevance");
        assertThat(PostgresSearchService.resolveSort(null, false)).isEqualTo("newest");
        assertThat(PostgresSearchService.resolveSort("relevance", false)).isEqualTo("newest");
        assertThat(PostgresSearchService.resolveSort("price_asc", false)).isEqualTo("price_asc");
    }

    @Test
    void likeWildcards_areEscaped() {
        assertThat(PostgresSearchService.escapeLike("50%_off\\")).isEqualTo("50\\%\\_off\\\\");
        assertThat(PostgresSearchService.escapeLike("walnut")).isEqualTo("walnut");
    }
}
