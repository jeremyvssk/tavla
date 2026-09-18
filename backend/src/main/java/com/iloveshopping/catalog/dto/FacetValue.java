// One selectable value in a facet (a brand or category) with the number of matching products.
package com.iloveshopping.catalog.dto;

public record FacetValue(String slug, String name, long count) {
}
