// Body of POST /staff/orders/{id}/notes: a note for the team on the order's history.
package com.iloveshopping.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrderNoteRequest(@NotBlank @Size(max = 1000) String note) {
}
