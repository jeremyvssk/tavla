// Response body carrying the in-memory access token (refresh token rides in an httpOnly cookie).
package com.iloveshopping.auth.dto;

public record TokenResponse(String accessToken) {
}
