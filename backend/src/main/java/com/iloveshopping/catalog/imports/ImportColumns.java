// The bulk upload's columns, shared by the file check and the product export so an exported file uploads again unchanged.
package com.iloveshopping.catalog.imports;

import com.iloveshopping.catalog.Product;
import com.iloveshopping.catalog.ProductImage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Categories and brands are named by slug (a brand by its name works too), never by id: ids differ
 * between a test shop and the real one, slugs don't. Product details the form calls "attributes" travel in
 * three columns of their own (ean, variant_group, variant); the rest of them stay as they are.
 */
public final class ImportColumns {

    public static final List<String> ALL = List.of("sku", "name", "description", "price", "stock", "category", "brand", "ean",
            "weight_kg", "width_cm", "height_cm", "depth_cm", "variant_group", "variant", "image_url", "image_alt");

    /** Between several photo URLs (or alt texts) in one cell. */
    static final String LIST_SEPARATOR = " | ";

    private ImportColumns() {
    }

    /** One product as a row of the upload's columns, in {@link #ALL}'s order. */
    public static List<String> exportRow(Product p, List<ProductImage> photos) {
        Map<String, Object> attrs = p.getAttributes() == null ? Map.of() : p.getAttributes();
        List<String> row = new ArrayList<>();
        row.add(text(p.getSku()));
        row.add(p.getName());
        row.add(text(p.getDescription()));
        row.add(p.getPrice().setScale(2).toPlainString());
        row.add(Integer.toString(p.getStockQuantity()));
        row.add(p.getCategory().getSlug());
        row.add(p.getBrand() == null ? "" : p.getBrand().getSlug());
        row.add(Objects.toString(attrs.get("ean"), ""));
        row.add(number(p.getWeightKg()));
        row.add(number(p.getWidthCm()));
        row.add(number(p.getHeightCm()));
        row.add(number(p.getDepthCm()));
        row.add(attrs.get("variant_group") instanceof String group ? group : "");
        row.add(attrs.get("variant") instanceof Map<?, ?> options ? variantText(options) : "");
        row.add(photos.stream().map(ProductImage::getUrl).collect(Collectors.joining(LIST_SEPARATOR)));
        // alt texts only when every photo has one, so the two lists can't fall out of step
        boolean allAlts = !photos.isEmpty() && photos.stream().allMatch(i -> i.getAltText() != null && !i.getAltText().isBlank()
                && !i.getAltText().contains("|"));
        row.add(allAlts ? photos.stream().map(ProductImage::getAltText).collect(Collectors.joining(LIST_SEPARATOR)) : "");
        return row;
    }

    /** "Colour: Blue, Size: Large", the variant cell's form. */
    static String variantText(Map<?, ?> options) {
        return options.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining(", "));
    }

    /** The reverse of {@link #variantText}; null when the text isn't in that form. */
    static Map<String, Object> parseVariant(String text) {
        Map<String, Object> options = new LinkedHashMap<>();
        for (String part : text.split(",")) {
            int colon = part.indexOf(':');
            if (colon < 0) {
                return null;
            }
            String key = part.substring(0, colon).strip();
            String value = part.substring(colon + 1).strip();
            if (key.isEmpty() || value.isEmpty() || key.length() > 50 || value.length() > 50 || options.containsKey(key)) {
                return null;
            }
            options.put(key, value);
        }
        return options.isEmpty() ? null : options;
    }

    private static String number(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
