// The bulk upload's check: reads every row against the shop as it is now and plans exactly what Import would write.
package com.iloveshopping.catalog.imports;

import com.iloveshopping.catalog.Brand;
import com.iloveshopping.catalog.BrandRepository;
import com.iloveshopping.catalog.Category;
import com.iloveshopping.catalog.CategoryService;
import com.iloveshopping.catalog.PriceGuard;
import com.iloveshopping.catalog.Product;
import com.iloveshopping.catalog.ProductImage;
import com.iloveshopping.catalog.ProductImageRepository;
import com.iloveshopping.catalog.ProductRepository;
import com.iloveshopping.catalog.dto.ImportChange;
import com.iloveshopping.catalog.dto.ImportIssue;
import com.iloveshopping.catalog.dto.ProductRequest;
import com.iloveshopping.catalog.dto.Skus;
import com.iloveshopping.csv.Csv;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Every row goes through the same rules a product saved from the edit page does (ProductRequest's own
 * annotations, through the Validator), plus the upload's own: the SKU is required and appears once, the
 * category and brand exist, a photo is an https link, and Sales may not move a price by more than half.
 *
 * <p>A row whose SKU is already in the shop updates that product: only the columns with a value change,
 * an empty cell leaves the old value alone. Nothing here writes; {@link Plan} is what Import would do.
 * Called at the check, and again inside Import's transaction, so the shop can't have changed in between.
 */
@Component
public class ImportChecker {

    /** Kept on the job and sent to the page; the counts are always whole. */
    static final int MAX_LISTED = 1_000;
    private static final int MAX_PHOTOS = 10;
    private static final Pattern PRICE = Pattern.compile("\\d{1,8}(\\.\\d{1,2})?");
    private static final Pattern WHOLE = Pattern.compile("\\d{1,9}");
    private static final Pattern DECIMAL = Pattern.compile("\\d{1,6}(\\.\\d{1,3})?");
    private static final Pattern EAN = Pattern.compile("\\d{8}|\\d{12,14}");
    private static final Pattern SKU = Pattern.compile(Skus.PATTERN);

    /** ProductRequest's fields, as the upload's columns call them. */
    private static final Map<String, String> COLUMN_OF = Map.ofEntries(
            Map.entry("name", "name"), Map.entry("description", "description"), Map.entry("price", "price"),
            Map.entry("stockQuantity", "stock"), Map.entry("categoryId", "category"), Map.entry("brandId", "brand"),
            Map.entry("attributes", "variant"), Map.entry("weightKg", "weight_kg"), Map.entry("widthCm", "width_cm"),
            Map.entry("heightCm", "height_cm"), Map.entry("depthCm", "depth_cm"), Map.entry("sku", "sku"));

    /** One product Import would create ({@code productId} null) or update. */
    public record Step(int line, UUID productId, ProductRequest request, List<Photo> photos) {
    }

    public record Photo(String url, String alt) {
    }

    /**
     * What the file would do: the steps for the rows without problems, and the problems found. {@code issues}
     * and {@code changes} are cut at {@link #MAX_LISTED}; {@code errorLines} is whole.
     */
    public record Plan(List<Step> steps, List<ImportIssue> issues, Set<Integer> errorLines, List<ImportChange> changes,
                       int created, int updated, int photos) {
    }

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final BrandRepository brands;
    private final CategoryService categoryService;
    private final Validator validator;

    public ImportChecker(ProductRepository products, ProductImageRepository images, BrandRepository brands,
                         CategoryService categoryService, Validator validator) {
        this.products = products;
        this.images = images;
        this.brands = brands;
        this.categoryService = categoryService;
        this.validator = validator;
    }

    public Plan check(ImportFileReader.RawFile file) {
        List<ImportFileReader.Row> rows = file.rows().stream().map(r -> ImportFileReader.cells(file, r)).toList();
        Context shop = context(rows);
        List<Step> steps = new ArrayList<>();
        List<ImportIssue> issues = new ArrayList<>();
        List<ImportChange> changes = new ArrayList<>();
        Map<String, Integer> firstLineOfSku = new HashMap<>();
        Set<Integer> errorLines = new LinkedHashSet<>();
        int created = 0;
        int updated = 0;
        int photos = 0;
        for (ImportFileReader.Row row : rows) {
            List<ImportIssue> found = new ArrayList<>(row.issues());
            List<ImportChange> rowChanges = new ArrayList<>();
            Step step = checkRow(row, shop, firstLineOfSku, found, rowChanges);
            if (!found.isEmpty()) {
                errorLines.add(row.line());
                issues.addAll(found);
                continue;
            }
            steps.add(step);
            changes.addAll(rowChanges);
            photos += step.photos().size();
            if (step.productId() == null) {
                created++;
            } else {
                updated++;
            }
        }
        return new Plan(steps, cap(issues), errorLines, cap(changes), created, updated, photos);
    }

    private Step checkRow(ImportFileReader.Row row, Context shop, Map<String, Integer> firstLineOfSku,
                          List<ImportIssue> found, List<ImportChange> changes) {
        int line = row.line();
        Map<String, String> cells = new LinkedHashMap<>();
        row.cells().forEach((column, raw) -> {
            if (Csv.isFormula(raw) && raw.charAt(0) != '-') {
                // = + @ tab or CR: a spreadsheet would run the cell. A leading - is a minus sign as often as not,
                // and a negative number fails its own rule below anyway.
                found.add(new ImportIssue(line, column, "Starts with = + @ or a tab, which a spreadsheet runs as a formula", raw));
            } else {
                cells.put(column, unescape(raw).strip());
            }
        });

        String sku = cells.get("sku");
        if (sku == null) {
            found.add(new ImportIssue(line, "sku", "Every row needs a SKU; rows are matched on it", null));
            return null;
        }
        if (sku.length() > Skus.MAX || !SKU.matcher(sku).matches()) {
            found.add(new ImportIssue(line, "sku", "Use " + Skus.MESSAGE, sku));
        }
        Integer first = firstLineOfSku.putIfAbsent(sku.toLowerCase(Locale.ROOT), line);
        if (first != null) {
            found.add(new ImportIssue(line, "sku", "Same SKU as row " + first, sku));
        }
        Product existing = shop.bySku().get(sku.toLowerCase(Locale.ROOT));

        if (existing == null) {
            for (String needed : List.of("name", "price", "stock", "category")) {
                if (!cells.containsKey(needed)) {
                    found.add(new ImportIssue(line, needed, "Needed for a new product", null));
                }
            }
        }
        BigDecimal price = parse(cells, "price", PRICE, "Write a price like 12.50, without a currency sign", line, found);
        BigDecimal stock = parse(cells, "stock", WHOLE, "Write a whole number, like 12", line, found);
        BigDecimal weight = parse(cells, "weight_kg", DECIMAL, "Write kilograms like 0.45", line, found);
        BigDecimal width = parse(cells, "width_cm", DECIMAL, "Write centimetres like 45.5", line, found);
        BigDecimal height = parse(cells, "height_cm", DECIMAL, "Write centimetres like 45.5", line, found);
        BigDecimal depth = parse(cells, "depth_cm", DECIMAL, "Write centimetres like 45.5", line, found);

        Category category = null;
        if (cells.containsKey("category")) {
            category = shop.categories().get(cells.get("category").toLowerCase(Locale.ROOT));
            if (category == null) {
                found.add(new ImportIssue(line, "category", "No category has this slug", cells.get("category")));
            }
        }
        Brand brand = null;
        if (cells.containsKey("brand")) {
            String key = cells.get("brand").toLowerCase(Locale.ROOT);
            brand = shop.brandsBySlug().getOrDefault(key, shop.brandsByName().get(key));
            if (brand == null) {
                found.add(new ImportIssue(line, "brand", "No brand has this slug or name. Add the brand first", cells.get("brand")));
            }
        }
        if (cells.containsKey("ean") && !EAN.matcher(cells.get("ean")).matches()) {
            found.add(new ImportIssue(line, "ean", "An EAN is 8, 12, 13 or 14 digits", cells.get("ean")));
        }
        if (cells.containsKey("variant_group") && cells.get("variant_group").length() > 100) {
            found.add(new ImportIssue(line, "variant_group", "At most 100 characters", cells.get("variant_group")));
        }
        Map<String, Object> variant = null;
        if (cells.containsKey("variant")) {
            variant = ImportColumns.parseVariant(cells.get("variant"));
            if (variant == null) {
                found.add(new ImportIssue(line, "variant", "Write it like Colour: Blue, Size: Large", cells.get("variant")));
            }
        }
        Set<String> onProduct = existing == null ? Set.of() : shop.photoUrls().getOrDefault(existing.getId(), Set.of());
        List<Photo> photos = photos(cells, onProduct, line, found);

        if (existing != null && price != null && !PriceGuard.allows(existing.getPrice(), price)) {
            found.add(new ImportIssue(line, "price", "Changes the price by more than 50%. An admin must make this change",
                    cells.get("price")));
        }
        if (!found.isEmpty()) {
            return null;
        }

        Map<String, Object> attributes = existing == null || existing.getAttributes() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(existing.getAttributes());
        if (cells.containsKey("ean")) {
            attributes.put("ean", cells.get("ean"));
        }
        if (cells.containsKey("variant_group")) {
            attributes.put("variant_group", cells.get("variant_group"));
        }
        if (variant != null) {
            attributes.put("variant", variant);
        }
        ProductRequest request = existing == null
                ? new ProductRequest(cells.get("name"), cells.get("description"), price, stock.intValueExact(), category.getId(),
                        brand == null ? null : brand.getId(), attributes.isEmpty() ? null : attributes, true,
                        weight, width, height, depth, sku)
                : new ProductRequest(
                        cells.getOrDefault("name", existing.getName()),
                        cells.getOrDefault("description", existing.getDescription()),
                        price != null ? price : existing.getPrice(),
                        stock != null ? stock.intValueExact() : existing.getStockQuantity(),
                        category != null ? category.getId() : existing.getCategory().getId(),
                        brand != null ? brand.getId() : existing.getBrand() == null ? null : existing.getBrand().getId(),
                        attributes.isEmpty() ? null : attributes, existing.isActive(),
                        weight != null ? weight : existing.getWeightKg(),
                        width != null ? width : existing.getWidthCm(),
                        height != null ? height : existing.getHeightCm(),
                        depth != null ? depth : existing.getDepthCm(),
                        existing.getSku());
        for (ConstraintViolation<ProductRequest> v : validator.validate(request)) {
            String field = v.getPropertyPath().iterator().next().getName();
            String column = COLUMN_OF.getOrDefault(field, field);
            found.add(new ImportIssue(line, column, capitalise(v.getMessage()), cells.get(column)));
        }
        if (!found.isEmpty()) {
            return null;
        }
        if (existing != null) {
            changes.addAll(changes(existing, request, cells, category, brand, photos));
        }
        return new Step(line, existing == null ? null : existing.getId(), request, photos);
    }

    /** What an update row would change, column by column; a cell equal to the stored value is no change. */
    private static List<ImportChange> changes(Product p, ProductRequest r, Map<String, String> cells, Category category,
                                              Brand brand, List<Photo> photos) {
        List<ImportChange> out = new ArrayList<>();
        String sku = p.getSku();
        String name = r.name();
        diff(out, sku, name, "name", p.getName(), r.name());
        diff(out, sku, name, "description", shorten(p.getDescription()), shorten(r.description()));
        diff(out, sku, name, "price", money(p.getPrice()), money(r.price()));
        diff(out, sku, name, "stock", Integer.toString(p.getStockQuantity()), Integer.toString(r.stockQuantity()));
        if (category != null) {
            diff(out, sku, name, "category", p.getCategory().getSlug(), category.getSlug());
        }
        if (brand != null) {
            diff(out, sku, name, "brand", p.getBrand() == null ? null : p.getBrand().getName(), brand.getName());
        }
        Map<String, Object> before = p.getAttributes() == null ? Map.of() : p.getAttributes();
        for (String key : List.of("ean", "variant_group")) {
            if (cells.containsKey(key)) {
                diff(out, sku, name, key, Objects.toString(before.get(key), null), cells.get(key));
            }
        }
        if (cells.containsKey("variant") && r.attributes() != null && r.attributes().get("variant") instanceof Map<?, ?> v) {
            diff(out, sku, name, "variant", before.get("variant") instanceof Map<?, ?> old ? ImportColumns.variantText(old) : null,
                    ImportColumns.variantText(v));
        }
        diff(out, sku, name, "weight_kg", plain(p.getWeightKg()), plain(r.weightKg()));
        diff(out, sku, name, "width_cm", plain(p.getWidthCm()), plain(r.widthCm()));
        diff(out, sku, name, "height_cm", plain(p.getHeightCm()), plain(r.heightCm()));
        diff(out, sku, name, "depth_cm", plain(p.getDepthCm()), plain(r.depthCm()));
        if (!photos.isEmpty()) {
            out.add(new ImportChange(sku, name, "image_url", null, "+" + photos.size() + (photos.size() == 1 ? " photo" : " photos")));
        }
        return out;
    }

    private static void diff(List<ImportChange> out, String sku, String name, String column, String from, String to) {
        if (to != null && !to.equals(from)) {
            out.add(new ImportChange(sku, name, column, from, to));
        }
    }

    /**
     * Photos are linked, not copied: the shop shows the supplier's image from its own address, as the demo
     * catalog does. Only https, so a shopper's browser never loads a mixed-content image. A URL the product
     * already shows is not added twice, and that one may be the shop's own /images/ path, so a file exported
     * from the shop uploads again unchanged.
     */
    private static List<Photo> photos(Map<String, String> cells, Set<String> onProduct, int line, List<ImportIssue> found) {
        if (!cells.containsKey("image_url")) {
            if (cells.containsKey("image_alt")) {
                found.add(new ImportIssue(line, "image_alt", "Alt text without an image_url", cells.get("image_alt")));
            }
            return List.of();
        }
        List<String> urls = split(cells.get("image_url"));
        List<String> alts = cells.containsKey("image_alt") ? split(cells.get("image_alt")) : List.of();
        if (urls.size() > MAX_PHOTOS) {
            found.add(new ImportIssue(line, "image_url", "At most " + MAX_PHOTOS + " photos per product", null));
            return List.of();
        }
        if (alts.size() > 1 && alts.size() != urls.size()) {
            found.add(new ImportIssue(line, "image_alt", "Give one alt text for all photos, or one per photo", cells.get("image_alt")));
            return List.of();
        }
        List<Photo> photos = new ArrayList<>();
        Set<String> inRow = new HashSet<>();
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            String alt = alts.isEmpty() ? null : alts.get(alts.size() == 1 ? 0 : i);
            if (alt != null && alt.length() > 255) {
                found.add(new ImportIssue(line, "image_alt", "At most 255 characters", alt));
            }
            if (onProduct.contains(url) || !inRow.add(url)) {
                continue;
            }
            if (!isHttpsUrl(url)) {
                found.add(new ImportIssue(line, "image_url", "Must be a full https:// address of at most 255 characters", url));
                continue;
            }
            photos.add(new Photo(url, alt));
        }
        return photos;
    }

    static boolean isHttpsUrl(String url) {
        if (url.length() > 255) {
            return false;
        }
        try {
            URI uri = new URI(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static List<String> split(String cell) {
        return Arrays.stream(cell.split("\\|")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    /** One query each for the shop's side of the whole file, not one per row. */
    private Context context(List<ImportFileReader.Row> rows) {
        Set<String> skus = new HashSet<>();
        rows.forEach(r -> {
            String sku = r.cells().get("sku");
            if (sku != null) {
                skus.add(unescape(sku).strip().toLowerCase(Locale.ROOT));
            }
        });
        Map<String, Product> bySku = new HashMap<>();
        if (!skus.isEmpty()) {
            products.findBySkuLowerIn(skus).forEach(p -> bySku.put(p.getSku().toLowerCase(Locale.ROOT), p));
        }
        Map<UUID, Set<String>> photoUrls = new HashMap<>();
        if (!bySku.isEmpty()) {
            for (ProductImage i : images.findByProductIdInOrderByPrimaryDescDisplayOrderAscIdAsc(
                    bySku.values().stream().map(Product::getId).toList())) {
                photoUrls.computeIfAbsent(i.getProductId(), k -> new HashSet<>()).add(i.getUrl());
            }
        }
        Map<String, Category> categories = new HashMap<>();
        categoryService.bySlug().forEach((slug, c) -> categories.put(slug.toLowerCase(Locale.ROOT), c));
        Map<String, Brand> brandsBySlug = new HashMap<>();
        Map<String, Brand> brandsByName = new HashMap<>();
        for (Brand b : brands.findAll()) {
            brandsBySlug.put(b.getSlug().toLowerCase(Locale.ROOT), b);
            brandsByName.putIfAbsent(b.getName().toLowerCase(Locale.ROOT), b);
        }
        return new Context(bySku, photoUrls, categories, brandsBySlug, brandsByName);
    }

    private record Context(Map<String, Product> bySku, Map<UUID, Set<String>> photoUrls, Map<String, Category> categories,
                           Map<String, Brand> brandsBySlug, Map<String, Brand> brandsByName) {
    }

    private static BigDecimal parse(Map<String, String> cells, String column, Pattern shape, String problem, int line,
                                    List<ImportIssue> found) {
        String value = cells.get(column);
        if (value == null) {
            return null;
        }
        if (!shape.matcher(value).matches()) {
            found.add(new ImportIssue(line, column, problem, value));
            return null;
        }
        return new BigDecimal(value);
    }

    /** The shop's own export writes '=x for a cell starting = + - or @ (Csv.cell); read it back as =x. */
    static String unescape(String cell) {
        return cell.length() > 1 && cell.charAt(0) == '\'' && Csv.isFormula(cell.substring(1)) ? cell.substring(1) : cell;
    }

    private static <T> List<T> cap(List<T> list) {
        return list.size() > MAX_LISTED ? List.copyOf(list.subList(0, MAX_LISTED)) : list;
    }

    private static String capitalise(String message) {
        return message.isEmpty() ? message : Character.toUpperCase(message.charAt(0)) + message.substring(1);
    }

    private static String shorten(String text) {
        return text == null || text.length() <= 60 ? text : text.substring(0, 59) + "…";
    }

    private static String money(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}
