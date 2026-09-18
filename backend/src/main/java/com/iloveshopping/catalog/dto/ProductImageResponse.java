// Public representation of one product image.
package com.iloveshopping.catalog.dto;

public record ProductImageResponse(Integer id, String url, String altText, Integer displayOrder, boolean primary) {
}
