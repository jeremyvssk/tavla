// Central mapping of domain and validation exceptions to HTTP responses.
package com.iloveshopping.exception;

import com.iloveshopping.auth.exception.InvalidCaptchaException;
import com.iloveshopping.auth.exception.InvalidCredentialsException;
import com.iloveshopping.auth.exception.InvalidOAuthTokenException;
import com.iloveshopping.auth.exception.InvalidRefreshTokenException;
import com.iloveshopping.auth.exception.InvalidResetTokenException;
import com.iloveshopping.auth.exception.InvalidTwoFactorCodeException;
import com.iloveshopping.auth.exception.TwoFactorAlreadyEnabledException;
import com.iloveshopping.catalog.exception.CatalogConflictException;
import com.iloveshopping.catalog.exception.CatalogNotFoundException;
import com.iloveshopping.catalog.exception.InvalidImageException;
import com.iloveshopping.catalog.exception.InvalidReferenceException;
import com.iloveshopping.catalog.exception.NotReviewOwnerException;
import com.iloveshopping.ratelimit.RateLimitExceededException;
import com.iloveshopping.user.exception.EmailAlreadyExistsException;
import com.iloveshopping.user.exception.EmailRegisteredWithPasswordException;
import com.iloveshopping.user.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new HashMap<>();
        // A binding failure's default message names Java types ("Failed to convert ... java.math.BigDecimal");
        // the client only needs to know the value was unusable.
        e.getBindingResult().getFieldErrors()
                .forEach(err -> fields.putIfAbsent(err.getField(),
                        err.isBindingFailure() ? "invalid value" : err.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ErrorResponse("validation_failed", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("malformed_request"));
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailExists(EmailAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("email_already_exists"));
    }

    @ExceptionHandler({InvalidCredentialsException.class, InvalidRefreshTokenException.class})
    public ResponseEntity<ErrorResponse> handleAuthFailure(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("invalid_credentials"));
    }

    @ExceptionHandler(InvalidOAuthTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOAuth(InvalidOAuthTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("invalid_oauth_token"));
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidResetToken(InvalidResetTokenException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_reset_token"));
    }

    @ExceptionHandler(InvalidCaptchaException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCaptcha(InvalidCaptchaException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("captcha_failed"));
    }

    @ExceptionHandler(InvalidTwoFactorCodeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTwoFactor(InvalidTwoFactorCodeException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("invalid_2fa_code"));
    }

    @ExceptionHandler(EmailRegisteredWithPasswordException.class)
    public ResponseEntity<ErrorResponse> handleEmailRegistered(EmailRegisteredWithPasswordException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("email_registered_with_password"));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("user_not_found"));
    }

    @ExceptionHandler(TwoFactorAlreadyEnabledException.class)
    public ResponseEntity<ErrorResponse> handleTwoFactorAlreadyEnabled(TwoFactorAlreadyEnabledException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("two_factor_already_enabled"));
    }

    // Constraint annotations on @RequestParam / @PathVariable (e.g. @Size on the suggestion query).
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
        Map<String, String> fields = new HashMap<>();
        e.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(err -> fields.putIfAbsent(name, err.getDefaultMessage()));
        });
        return ResponseEntity.badRequest().body(new ErrorResponse("validation_failed", fields));
    }

    // A path or query value of the wrong type, e.g. /products/not-a-uuid. Without this it would be a 500.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_parameter", Map.of(e.getName(), "invalid value")));
    }

    // Same body and header as RateLimitFilter's per-IP rejection, so the client handles one shape.
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimited(RateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(new ErrorResponse("too_many_requests"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(new ErrorResponse("image_too_large"));
    }

    @ExceptionHandler(CatalogNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCatalogNotFound(CatalogNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getCode()));
    }

    @ExceptionHandler(CatalogConflictException.class)
    public ResponseEntity<ErrorResponse> handleCatalogConflict(CatalogConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getCode()));
    }

    // A body that points at a category or brand that doesn't exist is bad input, not a missing URL.
    @ExceptionHandler(InvalidReferenceException.class)
    public ResponseEntity<ErrorResponse> handleInvalidReference(InvalidReferenceException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("validation_failed", Map.of(e.getField(), "does not exist")));
    }

    @ExceptionHandler(InvalidImageException.class)
    public ResponseEntity<ErrorResponse> handleInvalidImage(InvalidImageException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getCode()));
    }

    @ExceptionHandler(NotReviewOwnerException.class)
    public ResponseEntity<ErrorResponse> handleNotReviewOwner(NotReviewOwnerException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("forbidden"));
    }

    // Method-security denials reach the advice rather than SecurityConfig's handler; without this
    // the catch-all below would turn every 403 into a 500.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("forbidden"));
    }

    // Spring's own signalling exceptions (unknown path, wrong method) already carry a status;
    // keep it, and only re-shape the body so every error in this API looks the same.
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ErrorResponse> handleErrorResponse(ErrorResponseException e) {
        HttpStatusCode status = e.getStatusCode();
        String code = status instanceof HttpStatus s ? s.name().toLowerCase(Locale.ROOT) : "request_failed";
        return ResponseEntity.status(status).body(new ErrorResponse(code));
    }

    /**
     * Anything unmapped. The exception is logged in full and the client gets a generic body: an
     * error page that leaks a stack trace, a SQL fragment or an internal path is a finding of its
     * own, and the frontend can parse one error shape instead of two.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        // Several Spring MVC exceptions (missing parameter or multipart part, unsupported media type)
        // carry their own 4xx status through the ErrorResponse interface without extending
        // ErrorResponseException, so they land here. Keep their status rather than calling them a 500.
        if (e instanceof org.springframework.web.ErrorResponse framework && framework.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = framework.getStatusCode();
            String code = status instanceof HttpStatus s ? s.name().toLowerCase(Locale.ROOT) : "request_failed";
            return ResponseEntity.status(status).body(new ErrorResponse(code));
        }
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("internal_error"));
    }
}
