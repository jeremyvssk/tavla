// The staff's product list: every product whatever its state, filtered and counted per state, bulk changes and the CSV export.
package com.iloveshopping.catalog;

import com.iloveshopping.audit.AuditEvent;
import com.iloveshopping.audit.AuditKind;
import com.iloveshopping.audit.AuditLog;
import com.iloveshopping.audit.AuditTarget;
import com.iloveshopping.catalog.dto.BulkProductRequest;
import com.iloveshopping.catalog.dto.BulkProductResponse;
import com.iloveshopping.catalog.dto.StaffProductPageResponse;
import com.iloveshopping.catalog.dto.StaffProductParams;
import com.iloveshopping.catalog.dto.StaffProductRow;
import com.iloveshopping.catalog.exception.PriceChangeTooLargeException;
import com.iloveshopping.catalog.imports.ImportColumns;
import com.iloveshopping.csv.Csv;
import com.iloveshopping.exception.FieldValidationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The shop's own search (search/PostgresSearchService) shows only what shoppers may see. Staff need the
 * rest too, so this list is a plain filtered query over the products table instead, kept apart so the
 * shop's search never grows a "include hidden" switch someone could forget to turn off.
 */
@Service
public class StaffProductService {

    /** Below this many in stock, a product counts as low (the list's Low stock tab; ProductsPage's LOW). */
    static final int LOW_STOCK = 5;
    private static final int EXPORT_LIMIT = 20_000;
    private static final int EXPORT_CHUNK = 1_000;
    private static final List<String> STATES = List.of("all", "active", "hidden", "out", "low", "archived");

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final CategoryService categoryService;
    private final AuditLog auditLog;

    public StaffProductService(ProductRepository products, ProductImageRepository images, CategoryService categoryService,
                               AuditLog auditLog) {
        this.products = products;
        this.images = images;
        this.categoryService = categoryService;
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public StaffProductPageResponse list(StaffProductParams params) {
        int page = params.page() == null ? 0 : params.page();
        int size = params.size() == null ? 50 : params.size();
        Specification<Product> filters = filters(params);
        Page<Product> found = products.findAll(filters.and(state(params.state())), PageRequest.of(page, size, sort(params.sort())));
        Map<UUID, ProductImage> firstImage = firstImages(found.getContent().stream().map(Product::getId).toList());
        List<StaffProductRow> rows = found.getContent().stream().map(p -> row(p, firstImage.get(p.getId()))).toList();
        // one count per tab: six small counts read better than one query of six FILTER clauses, and the
        // tabs show what each would hold under the same search and filters
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String state : STATES) {
            counts.put(state, products.count(filters.and(state(state))));
        }
        return new StaffProductPageResponse(rows, page, size, found.getTotalElements(), found.getTotalPages(), counts);
    }

    /** In stock but fewer than {@link #LOW_STOCK} left, archived ones left out: the overview's "running low". */
    @Transactional(readOnly = true)
    public long lowStockCount() {
        return products.count(state("low"));
    }

    /**
     * The filtered list as a file in the bulk upload's own columns, so it can be edited in a spreadsheet and
     * uploaded again. At most 20,000 rows; photos are listed by URL, joined with " | ".
     */
    @Transactional(readOnly = true)
    public byte[] export(StaffProductParams params) {
        List<Product> found = products.findAll(filters(params).and(state(params.state())),
                PageRequest.of(0, EXPORT_LIMIT, sort(params.sort()))).getContent();
        List<List<?>> rows = new ArrayList<>();
        for (int from = 0; from < found.size(); from += EXPORT_CHUNK) {
            List<Product> chunk = found.subList(from, Math.min(found.size(), from + EXPORT_CHUNK));
            Map<UUID, List<ProductImage>> photos = new HashMap<>();
            images.findByProductIdInOrderByPrimaryDescDisplayOrderAscIdAsc(chunk.stream().map(Product::getId).toList())
                    .forEach(i -> photos.computeIfAbsent(i.getProductId(), k -> new ArrayList<>()).add(i));
            for (Product p : chunk) {
                rows.add(ImportColumns.exportRow(p, photos.getOrDefault(p.getId(), List.of())));
            }
        }
        return Csv.file(ImportColumns.ALL, rows);
    }

    /**
     * One change to many products, all or nothing. A price change Sales may not make on any one of them
     * refuses the whole request before anything is written, rather than changing some and not others.
     * Products already in the asked-for state are left alone and not counted; each one changed gets its
     * own audit entry, so the log answers "who changed this product" by product.
     */
    @Transactional
    public BulkProductResponse bulk(BulkProductRequest request) {
        List<Product> found = products.findAllById(new LinkedHashSet<>(request.ids()));
        int changed = switch (request.action()) {
            case "show" -> setActive(found, true);
            case "hide" -> setActive(found, false);
            case "move" -> move(found, request.categoryId());
            default -> price(found, request.mode(), request.value());
        };
        products.flush();
        return new BulkProductResponse(changed);
    }

    private int setActive(List<Product> found, boolean active) {
        int changed = 0;
        for (Product p : found) {
            if (p.isActive() == active) {
                continue;
            }
            String before = p.getArchivedAt() != null ? "Archived" : p.isActive() ? "Active" : "Hidden";
            p.setActive(active);
            if (active) {
                p.setArchivedAt(null);
            }
            changed++;
            auditLog.record(AuditEvent.of(AuditKind.PRODUCTS, active ? "Showed {target} in the shop" : "Hid {target}")
                    .target(AuditTarget.PRODUCT, p.getId(), p.getName()).change(before, active ? "Active" : "Hidden"));
        }
        return changed;
    }

    private int move(List<Product> found, Integer categoryId) {
        if (categoryId == null) {
            throw new FieldValidationException("categoryId", "is needed to move products");
        }
        Category to = categoryService.require(categoryId);
        int changed = 0;
        for (Product p : found) {
            if (p.getCategory().getId().equals(to.getId())) {
                continue;
            }
            String from = p.getCategory().getName();
            p.setCategory(to);
            changed++;
            auditLog.record(AuditEvent.of(AuditKind.PRODUCTS, "Moved {target}")
                    .target(AuditTarget.PRODUCT, p.getId(), p.getName()).change(from, to.getName()));
        }
        return changed;
    }

    private int price(List<Product> found, String mode, BigDecimal value) {
        Map<String, String> missing = new LinkedHashMap<>();
        if (mode == null) {
            missing.put("mode", "is needed to change prices");
        }
        if (value == null) {
            missing.put("value", "is needed to change prices");
        }
        if (!missing.isEmpty()) {
            throw new FieldValidationException(missing);
        }
        if (!"set".equals(mode) && value.signum() == 0) {
            throw new FieldValidationException("value", "must be more than 0%");
        }
        if ("lower".equals(mode) && value.compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new FieldValidationException("value", "must be less than 100%");
        }
        if ("raise".equals(mode) && value.compareTo(BigDecimal.valueOf(1000)) > 0) {
            throw new FieldValidationException("value", "must be at most 1000%");
        }
        Map<Product, BigDecimal> next = new LinkedHashMap<>();
        for (Product p : found) {
            BigDecimal to = newPrice(p.getPrice(), mode, value);
            if (to.compareTo(p.getPrice()) != 0) {
                next.put(p, to);
            }
        }
        // checked for every product before any is written
        if (!next.entrySet().stream().allMatch(e -> PriceGuard.allows(e.getKey().getPrice(), e.getValue()))) {
            throw new PriceChangeTooLargeException();
        }
        next.forEach((p, to) -> {
            auditLog.record(AuditEvent.of(AuditKind.PRODUCTS, "Changed the price of {target}")
                    .target(AuditTarget.PRODUCT, p.getId(), p.getName())
                    .change(ProductService.euros(p.getPrice()), ProductService.euros(to)));
            p.setPrice(to);
        });
        return next.size();
    }

    /** Rounded half up to the cent: raising €9.99 by 10% is €10.99, not €10.989. */
    static BigDecimal newPrice(BigDecimal price, String mode, BigDecimal value) {
        BigDecimal hundred = BigDecimal.valueOf(100);
        BigDecimal result = switch (mode) {
            case "set" -> value;
            case "raise" -> price.multiply(hundred.add(value)).divide(hundred);
            default -> price.multiply(hundred.subtract(value)).divide(hundred);
        };
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    /** Everything but the state, which the counts vary over. */
    private Specification<Product> filters(StaffProductParams params) {
        Specification<Product> spec = Specification.unrestricted();
        if (params.q() != null && !params.q().isBlank()) {
            String text = escapeLike(params.q().strip().toLowerCase(Locale.ROOT));
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), "%" + text + "%", '\\'),
                    cb.like(cb.lower(root.get("sku")), text + "%", '\\')));
        }
        if (params.category() != null && !params.category().isBlank()) {
            Set<Integer> shelves = categoryService.subtreeIds(params.category());
            spec = spec.and((root, query, cb) -> shelves.isEmpty() ? cb.disjunction() : root.get("category").get("id").in(shelves));
        }
        if (params.brand() != null && !params.brand().isBlank()) {
            String brand = params.brand();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("brand").get("slug"), brand));
        }
        return spec;
    }

    private static Specification<Product> state(String state) {
        return switch (state == null ? "all" : state) {
            case "active" -> (root, query, cb) -> cb.isTrue(root.get("active"));
            case "hidden" -> (root, query, cb) -> cb.and(cb.isFalse(root.get("active")), cb.isNull(root.get("archivedAt")));
            case "out" -> (root, query, cb) -> cb.and(cb.isNull(root.get("archivedAt")), cb.equal(root.get("stockQuantity"), 0));
            case "low" -> (root, query, cb) -> cb.and(cb.isNull(root.get("archivedAt")),
                    cb.between(root.get("stockQuantity"), 1, LOW_STOCK - 1));
            case "archived" -> (root, query, cb) -> cb.isNotNull(root.get("archivedAt"));
            default -> (root, query, cb) -> cb.isNull(root.get("archivedAt"));
        };
    }

    /** From an allowlist, never from the request string; the id last so pages never overlap. */
    private static Sort sort(String sort) {
        Sort.Order first = switch (sort == null ? "newest" : sort) {
            case "price_desc" -> Sort.Order.desc("price");
            case "price_asc" -> Sort.Order.asc("price");
            case "stock_asc" -> Sort.Order.asc("stockQuantity");
            case "stock_desc" -> Sort.Order.desc("stockQuantity");
            case "rating" -> Sort.Order.desc("averageRating").nullsLast();
            default -> Sort.Order.desc("createdAt");
        };
        return Sort.by(first, Sort.Order.asc("id"));
    }

    private Map<UUID, ProductImage> firstImages(List<UUID> ids) {
        Map<UUID, ProductImage> first = new HashMap<>();
        if (!ids.isEmpty()) {
            images.findByProductIdInOrderByPrimaryDescDisplayOrderAscIdAsc(ids).forEach(i -> first.putIfAbsent(i.getProductId(), i));
        }
        return first;
    }

    private static StaffProductRow row(Product p, ProductImage image) {
        String state = p.getArchivedAt() != null ? "ARCHIVED" : p.isActive() ? "ACTIVE" : "HIDDEN";
        return new StaffProductRow(p.getId(), p.getName(), p.getSku(), p.getBrand() == null ? null : p.getBrand().getName(),
                p.getCategory().getSlug(), p.getPrice(), p.getStockQuantity(), p.getStockQuantity() > 0, p.getAverageRating(),
                Objects.requireNonNullElse(p.getReviewCount(), 0), state,
                image == null ? null : image.getUrl(), image == null ? null : image.getFraming());
    }

    /** What was typed is matched literally: % and _ are characters, not wildcards. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
