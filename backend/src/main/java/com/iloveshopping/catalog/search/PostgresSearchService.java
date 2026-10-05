// SearchService on Postgres: full-text ranking, SQL facet counts, category subtrees, trigram suggestions.
package com.iloveshopping.catalog.search;

import com.iloveshopping.catalog.dto.FacetValue;
import com.iloveshopping.catalog.dto.ImageFraming;
import com.iloveshopping.catalog.dto.Facets;
import com.iloveshopping.catalog.dto.PriceBucket;
import com.iloveshopping.catalog.dto.ProductSearchParams;
import com.iloveshopping.catalog.dto.ProductSearchResponse;
import com.iloveshopping.catalog.dto.ProductSummary;
import com.iloveshopping.catalog.dto.RatingBucket;
import com.iloveshopping.catalog.dto.Suggestion;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hand-written SQL through JdbcClient rather than JPA: ts_rank, the @@ operator, FILTER aggregates
 * and WITH RECURSIVE are all things JPA can't express, and the SQL here is exactly the SQL that
 * runs, so EXPLAIN ANALYZE on it is meaningful.
 *
 * <p>Injection safety rule for this class: every string concatenated into SQL is a constant
 * defined in this file. Request values only ever travel as named parameters.
 */
@Service
public class PostgresSearchService implements SearchService {

    static final int DEFAULT_SIZE = 24;
    static final int SUGGESTION_LIMIT = 8;
    static final int SUGGESTION_MIN_LENGTH = 2;

    /** Price facet bands: min inclusive, max exclusive, the same rule minPrice/maxPrice use. */
    static final List<BigDecimal[]> PRICE_BANDS = List.of(
            new BigDecimal[]{BigDecimal.ZERO, new BigDecimal("25")},
            new BigDecimal[]{new BigDecimal("25"), new BigDecimal("100")},
            new BigDecimal[]{new BigDecimal("100"), new BigDecimal("250")},
            new BigDecimal[]{new BigDecimal("250"), null});

    static final int[] RATING_THRESHOLDS = {4, 3, 2, 1};

    /**
     * How close a name word must be for the typo fallback. Postgres's default of 0.6 misses
     * "stanton" (0.55 against Staunton); 0.4 lets "bord" match every "Box" and "Boxwood" in the
     * seed. Measured on the seeded names, 0.5 catches typos and prefixes without that noise.
     */
    static final String FUZZY_THRESHOLD = "0.5";

    private static final String RELEVANCE = "ts_rank(p.search_vector, websearch_to_tsquery('english', :q)) DESC, p.id";
    private static final String FUZZY_RELEVANCE = "word_similarity(:q, p.name) DESC, p.id";

    /**
     * The default browse order, the one shops call "Featured" or "Recommended". Buyable before
     * sold out, photographed before not, then rating. We have no sales data yet, so rating is the
     * popularity signal, and it is a Bayesian average: each product starts as if it already had
     * three 3.5-star reviews, so one 5-star review does not outrank forty reviews averaging 4.7.
     */
    private static final String FEATURED = """
            (p.stock_quantity > 0) DESC,
            EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = p.id) DESC,
            (COALESCE(p.average_rating, 0) * p.review_count + 3.5 * 3) / (p.review_count + 3) DESC,
            p.created_at DESC, p.id""";

    /** The sort allowlist. A value not in this map never reaches the query. */
    private static final Map<String, String> ORDER_BY = Map.of(
            "featured", FEATURED,
            "relevance", RELEVANCE,
            "price_asc", "p.price ASC, p.id",
            "price_desc", "p.price DESC, p.id",
            "rating", "p.average_rating DESC NULLS LAST, p.review_count DESC, p.id",
            "newest", "p.created_at DESC, p.id");

    // The clicked category plus every descendant, so browsing "Chess" includes "Chess > Boards > Folding".
    private static final String CATEGORY_SUBTREE = """
            p.category_id IN (
                WITH RECURSIVE subtree AS (
                    SELECT id FROM categories WHERE slug = :category AND active
                    UNION ALL
                    SELECT c.id FROM categories c JOIN subtree s ON c.parent_id = s.id WHERE c.active
                )
                SELECT id FROM subtree)""";

    /** Which filter a facet query leaves out. See {@link #where}. */
    private enum Omit { NONE, BRAND, PRICE, RATING }

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public PostgresSearchService(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductSearchResponse search(ProductSearchParams params) {
        Criteria c = Criteria.from(params);
        Map<String, Object> args = c.args();

        long total = count(c, args);
        if (total == 0 && c.q() != null) {
            // Full-text search matches whole words, so "wal" or "stanton" finds nothing. Retry the
            // same filters against name trigrams. The setting is transaction-local, and %> with it
            // is served by the trigram index, unlike a word_similarity(...) >= x comparison.
            jdbc.sql("SELECT set_config('pg_trgm.word_similarity_threshold', :threshold, true)")
                    .param("threshold", FUZZY_THRESHOLD).query(String.class).single();
            c = c.asFuzzy();
            total = count(c, args);
        }

        String sort = resolveSort(params.sort(), c.q() != null);
        int page = params.page() == null ? 0 : params.page();
        int size = params.size() == null ? DEFAULT_SIZE : params.size();
        args.put("limit", size);
        args.put("offset", (long) page * size);
        String orderBy = c.fuzzy() && sort.equals("relevance") ? FUZZY_RELEVANCE : ORDER_BY.get(sort);

        List<ProductSummary> items = jdbc.sql("""
                        SELECT p.id, p.name, p.price, p.average_rating, p.review_count, p.stock_quantity,
                               b.name AS brand_name, c.slug AS category_slug,
                               img.url AS image_url, img.framing::text AS image_framing
                        FROM products p
                        JOIN categories c ON c.id = p.category_id
                        LEFT JOIN brands b ON b.id = p.brand_id
                        LEFT JOIN LATERAL (SELECT i.url, i.framing FROM product_images i WHERE i.product_id = p.id
                                           ORDER BY i.is_primary DESC, i.display_order NULLS LAST, i.id LIMIT 1) img ON true
                        WHERE\s""" + where(c, Omit.NONE) + """

                        ORDER BY\s""" + orderBy + """

                        LIMIT :limit OFFSET :offset""")
                .params(args)
                .query((rs, row) -> new ProductSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getBigDecimal("price"),
                        rs.getBigDecimal("average_rating"),
                        rs.getInt("review_count"),
                        rs.getInt("stock_quantity") > 0,
                        rs.getString("brand_name"),
                        rs.getString("category_slug"),
                        rs.getString("image_url"),
                        framing(rs.getString("image_framing"))))
                .list();

        Facets facets = new Facets(categoryFacet(c, args), brandFacet(c, args), priceFacet(c, args), ratingFacet(c, args));
        int totalPages = (int) ((total + size - 1) / size);
        return new ProductSearchResponse(items, page, size, total, totalPages, sort, c.fuzzy(), facets);
    }

    private ImageFraming framing(String text) {
        return text == null ? null : json.readValue(text, ImageFraming.class);
    }

    private long count(Criteria c, Map<String, Object> args) {
        return jdbc.sql("SELECT count(*) FROM products p WHERE " + where(c, Omit.NONE))
                .params(args).query(Long.class).single();
    }

    /**
     * Builds the one WHERE clause that the rows, the total and every facet share, so the counts
     * next to the filters can never disagree with the list.
     *
     * <p>A facet query omits its own filter. With the brand "rank-and-file" ticked, the brand
     * facet still counts every other brand, so a shopper can tick a second one instead of seeing
     * zeros. The category is not omitted: it is where the shopper is, not a refinement, so the
     * category facet shows the subcategories inside it.
     */
    private String where(Criteria c, Omit omit) {
        List<String> clauses = new ArrayList<>();
        clauses.add("p.active");
        if (c.q() != null && c.fuzzy()) {
            clauses.add("p.name %> :q");
        } else if (c.q() != null) {
            // websearch_to_tsquery never throws on user syntax, unlike to_tsquery, and understands
            // "quoted phrases", OR and -exclusions.
            clauses.add("p.search_vector @@ websearch_to_tsquery('english', :q)");
        }
        if (c.category() != null) {
            clauses.add(CATEGORY_SUBTREE);
        }
        if (!c.brands().isEmpty() && omit != Omit.BRAND) {
            clauses.add("p.brand_id IN (SELECT id FROM brands WHERE slug IN (:brands))");
        }
        if (c.minPrice() != null && omit != Omit.PRICE) {
            clauses.add("p.price >= :minPrice");
        }
        if (c.maxPrice() != null && omit != Omit.PRICE) {
            clauses.add("p.price < :maxPrice");
        }
        if (c.minRating() != null && omit != Omit.RATING) {
            clauses.add("p.average_rating >= :minRating");
        }
        return String.join("\n  AND ", clauses);
    }

    private List<FacetValue> categoryFacet(Criteria c, Map<String, Object> args) {
        return jdbc.sql("""
                        SELECT c.slug, c.name, count(*) AS n
                        FROM products p JOIN categories c ON c.id = p.category_id
                        WHERE\s""" + where(c, Omit.NONE) + """

                        GROUP BY c.slug, c.name
                        ORDER BY n DESC, c.name""")
                .params(args)
                .query((rs, row) -> new FacetValue(rs.getString("slug"), rs.getString("name"), rs.getLong("n")))
                .list();
    }

    private List<FacetValue> brandFacet(Criteria c, Map<String, Object> args) {
        return jdbc.sql("""
                        SELECT b.slug, b.name, count(*) AS n
                        FROM products p JOIN brands b ON b.id = p.brand_id
                        WHERE\s""" + where(c, Omit.BRAND) + """

                        GROUP BY b.slug, b.name
                        ORDER BY n DESC, b.name""")
                .params(args)
                .query((rs, row) -> new FacetValue(rs.getString("slug"), rs.getString("name"), rs.getLong("n")))
                .list();
    }

    /** All bands in one pass over the matched rows, using FILTER instead of one query per band. */
    private List<PriceBucket> priceFacet(Criteria c, Map<String, Object> args) {
        StringBuilder select = new StringBuilder("SELECT ");
        Map<String, Object> bandArgs = new HashMap<>(args);
        for (int i = 0; i < PRICE_BANDS.size(); i++) {
            BigDecimal[] band = PRICE_BANDS.get(i);
            bandArgs.put("bandMin" + i, band[0]);
            select.append(i == 0 ? "" : ", ")
                    .append("count(*) FILTER (WHERE p.price >= :bandMin").append(i);
            if (band[1] != null) {
                bandArgs.put("bandMax" + i, band[1]);
                select.append(" AND p.price < :bandMax").append(i);
            }
            select.append(") AS band").append(i);
        }
        select.append(" FROM products p WHERE ").append(where(c, Omit.PRICE));
        return jdbc.sql(select.toString()).params(bandArgs)
                .query((rs, row) -> {
                    List<PriceBucket> buckets = new ArrayList<>();
                    for (int i = 0; i < PRICE_BANDS.size(); i++) {
                        buckets.add(new PriceBucket(PRICE_BANDS.get(i)[0], PRICE_BANDS.get(i)[1], rs.getLong("band" + i)));
                    }
                    return buckets;
                })
                .single();
    }

    private List<RatingBucket> ratingFacet(Criteria c, Map<String, Object> args) {
        StringBuilder select = new StringBuilder("SELECT ");
        for (int i = 0; i < RATING_THRESHOLDS.length; i++) {
            select.append(i == 0 ? "" : ", ")
                    .append("count(*) FILTER (WHERE p.average_rating >= ").append(RATING_THRESHOLDS[i])
                    .append(") AS stars").append(RATING_THRESHOLDS[i]);
        }
        select.append(" FROM products p WHERE ").append(where(c, Omit.RATING));
        return jdbc.sql(select.toString()).params(args)
                .query((rs, row) -> {
                    List<RatingBucket> buckets = new ArrayList<>();
                    for (int threshold : RATING_THRESHOLDS) {
                        buckets.add(new RatingBucket(threshold, rs.getLong("stars" + threshold)));
                    }
                    return buckets;
                })
                .single();
    }

    /**
     * Trigram match on the product name. ILIKE with a leading % would normally force a full scan;
     * the GIN gin_trgm_ops index from V5 is what keeps it fast. LIKE wildcards in the input are
     * escaped, otherwise typing "%" would match the entire catalog.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Suggestion> suggest(String query) {
        String q = query == null ? "" : query.strip();
        if (q.length() < SUGGESTION_MIN_LENGTH) {
            return List.of();
        }
        return jdbc.sql("""
                        SELECT p.id, p.name
                        FROM products p
                        WHERE p.active AND p.name ILIKE :pattern ESCAPE '\\'
                        ORDER BY similarity(p.name, :q) DESC, p.name
                        LIMIT :limit""")
                .param("pattern", "%" + escapeLike(q) + "%")
                .param("q", q)
                .param("limit", SUGGESTION_LIMIT)
                .query((rs, row) -> new Suggestion(rs.getObject("id", UUID.class), rs.getString("name")))
                .list();
    }

    /** Relevance needs a query to rank against; without one, browsing falls back to featured. */
    static String resolveSort(String requested, boolean hasQuery) {
        if (requested == null || ("relevance".equals(requested) && !hasQuery)) {
            return hasQuery ? "relevance" : "featured";
        }
        return requested;
    }

    static String escapeLike(String input) {
        return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * Normalised filters: blank strings become null, so "?q=" behaves like no search at all.
     * {@code fuzzy} switches q from full-text matching to the name-trigram fallback.
     */
    record Criteria(String q, String category, List<String> brands,
                    BigDecimal minPrice, BigDecimal maxPrice, BigDecimal minRating, boolean fuzzy) {

        static Criteria from(ProductSearchParams p) {
            return new Criteria(blankToNull(p.q()), blankToNull(p.category()),
                    p.brand() == null ? List.of() : p.brand().stream().filter(b -> !b.isBlank()).distinct().toList(),
                    p.minPrice(), p.maxPrice(), p.minRating(), false);
        }

        Criteria asFuzzy() {
            return new Criteria(q, category, brands, minPrice, maxPrice, minRating, true);
        }

        Map<String, Object> args() {
            Map<String, Object> args = new HashMap<>();
            args.put("q", q);
            args.put("category", category);
            args.put("brands", brands);
            args.put("minPrice", minPrice);
            args.put("maxPrice", maxPrice);
            args.put("minRating", minRating);
            return args;
        }

        private static String blankToNull(String s) {
            return s == null || s.isBlank() ? null : s.strip();
        }
    }
}
