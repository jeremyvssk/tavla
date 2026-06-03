// Response body returned after a successful registration.
package com.iloveshopping.auth.dto;

import java.util.UUID;

public record RegisterResponse(UUID id, String email) {
}
