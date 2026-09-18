// One "N stars and up" option in the rating facet.
package com.iloveshopping.catalog.dto;

public record RatingBucket(int minRating, long count) {
}
