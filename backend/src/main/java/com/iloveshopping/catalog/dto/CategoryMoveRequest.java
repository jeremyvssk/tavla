// Body of PUT /categories/{id}/position: put the shelf under parentId, just before beforeId (null: last).
package com.iloveshopping.catalog.dto;

import jakarta.validation.constraints.Positive;

public record CategoryMoveRequest(@Positive Integer parentId, @Positive Integer beforeId) {
}
