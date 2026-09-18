// A stable page shape for JSON; Spring's Page is not serialized directly because its JSON form isn't a contract.
package com.iloveshopping.catalog.dto;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
}
