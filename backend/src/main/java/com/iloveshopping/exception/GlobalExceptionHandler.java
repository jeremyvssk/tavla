// Central mapping of domain and validation exceptions to HTTP responses.
package com.iloveshopping.exception;

import com.iloveshopping.auth.exception.InvalidCaptchaException;
import com.iloveshopping.auth.exception.InvalidCredentialsException;
import com.iloveshopping.auth.exception.InvalidOAuthTokenException;
import com.iloveshopping.auth.exception.InvalidRefreshTokenException;
import com.iloveshopping.auth.exception.InvalidResetTokenException;
import com.iloveshopping.auth.exception.InvalidTwoFactorCodeException;
import com.iloveshopping.auth.exception.TwoFactorAlreadyEnabledException;
import com.iloveshopping.user.exception.EmailAlreadyExistsException;
import com.iloveshopping.user.exception.EmailRegisteredWithPasswordException;
import com.iloveshopping.user.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new HashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(err -> fields.putIfAbsent(err.getField(), err.getDefaultMessage()));
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
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("internal_error"));
    }
}
