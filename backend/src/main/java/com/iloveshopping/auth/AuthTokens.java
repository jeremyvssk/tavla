// Internal pair of freshly issued tokens; the controller routes each to its transport.
package com.iloveshopping.auth;

record AuthTokens(String accessToken, String refreshToken) {
}
