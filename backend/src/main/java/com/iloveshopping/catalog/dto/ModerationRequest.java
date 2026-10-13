// Body of POST /staff/reviews/{keep|hide|unhide}: the reviews, and for hide the reason.
package com.iloveshopping.catalog.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** reason is needed to hide; the author is shown it next to their hidden review. */
public record ModerationRequest(@NotEmpty @Size(max = 100) List<@NotNull UUID> ids, @Size(max = 200) String reason) {

    public record Result(int done) {
    }
}
