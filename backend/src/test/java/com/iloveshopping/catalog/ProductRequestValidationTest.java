// Unit tests for the product data model's validation rules, without Spring or a database.
package com.iloveshopping.catalog;

import com.iloveshopping.catalog.dto.ProductRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRequestValidationTest {

    private static jakarta.validation.ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void aCompleteProduct_isValid() {
        assertThat(violations(valid())).isEmpty();
    }

    @Test
    void nameIsRequired_andBoundedByTheColumnWidth() {
        assertThat(violations(with(valid(), "name", " "))).containsExactly("name");
        assertThat(violations(with(valid(), "name", "x".repeat(256)))).containsExactly("name");
        assertThat(violations(with(valid(), "name", "x".repeat(255)))).isEmpty();
    }

    @Test
    void priceMustBeNonNegative_andFitDecimal10_2() {
        assertThat(violations(with(valid(), "price", new BigDecimal("-0.01")))).containsExactly("price");
        assertThat(violations(with(valid(), "price", new BigDecimal("1.999")))).containsExactly("price");
        assertThat(violations(with(valid(), "price", new BigDecimal("123456789.00")))).containsExactly("price");
        assertThat(violations(with(valid(), "price", new BigDecimal("0.00")))).isEmpty();
    }

    @Test
    void stockCannotBeNegative_andCategoryIsRequired() {
        assertThat(violations(with(valid(), "stockQuantity", -1))).containsExactly("stockQuantity");
        assertThat(violations(with(valid(), "categoryId", null))).containsExactly("categoryId");
    }

    @Test
    void measurementsMustBePositiveWhenGiven() {
        assertThat(violations(with(valid(), "weightKg", BigDecimal.ZERO))).containsExactly("weightKg");
        assertThat(violations(with(valid(), "widthCm", new BigDecimal("-3")))).containsExactly("widthCm");
        assertThat(violations(with(valid(), "weightKg", null))).isEmpty();
    }

    @Test
    void attributesAreCapped() {
        Map<String, Object> tooMany = IntStream.range(0, 31).boxed()
                .collect(Collectors.toMap(i -> "k" + i, i -> (Object) i));
        assertThat(violations(with(valid(), "attributes", tooMany))).containsExactly("attributes");
    }

    private Set<String> violations(ProductRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static ProductRequest valid() {
        return new ProductRequest("Walnut Board", "Solid walnut", new BigDecimal("149.00"), 3, 1, 2,
                Map.of("wood", "walnut"), true,
                new BigDecimal("2.5"), new BigDecimal("50"), new BigDecimal("2"), new BigDecimal("50"));
    }

    /** Records are immutable, so copy one with a single component swapped. */
    private static ProductRequest with(ProductRequest r, String field, Object value) {
        return new ProductRequest(
                pick(field, "name", value, r.name()),
                pick(field, "description", value, r.description()),
                pick(field, "price", value, r.price()),
                pick(field, "stockQuantity", value, r.stockQuantity()),
                pick(field, "categoryId", value, r.categoryId()),
                pick(field, "brandId", value, r.brandId()),
                pick(field, "attributes", value, r.attributes()),
                pick(field, "active", value, r.active()),
                pick(field, "weightKg", value, r.weightKg()),
                pick(field, "widthCm", value, r.widthCm()),
                pick(field, "heightCm", value, r.heightCm()),
                pick(field, "depthCm", value, r.depthCm()));
    }

    @SuppressWarnings("unchecked")
    private static <T> T pick(String field, String name, Object value, T current) {
        return field.equals(name) ? (T) value : current;
    }
}
