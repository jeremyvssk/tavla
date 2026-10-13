// Body of PUT /staff/users/{id}/role: the new role, and the changer's own code from their authenticator app.
package com.iloveshopping.user.dto;

import com.iloveshopping.user.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RoleChangeRequest(
        @NotNull Role role,
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be the 6 digits from your authenticator app") String code) {
}
