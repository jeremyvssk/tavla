// Body of POST /products/{id}/reviews/{reviewId}/report: why the shopper thinks it shouldn't be there.
package com.iloveshopping.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewReportRequest(@NotBlank @Size(max = 200) String reason) {
}
