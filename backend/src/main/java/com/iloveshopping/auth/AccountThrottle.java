// Per-account limits on login attempts and reset emails, layered so they can't be used to lock a real user out.
package com.iloveshopping.auth;

import com.iloveshopping.ratelimit.RateLimitExceededException;
import com.iloveshopping.ratelimit.RateLimiter;
import com.iloveshopping.user.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Per-IP limits (RateLimitFilter) miss a guesser spread over many addresses, so login is also
 * counted per account, in two layers (OWASP Authentication Cheat Sheet):
 * <ul>
 *   <li>account + IP, strict: one address guessing one account is stopped after a handful of tries,
 *       and the lockout only applies to that address, never to the real user elsewhere.</li>
 *   <li>account alone, loose: catches a distributed guesser. An attacker can trip it on purpose,
 *       but only from several addresses, since each address stops counting once its own pair
 *       limit is hit. Password reset clears it, so the real user always has a way back in.</li>
 * </ul>
 * Every attempt counts and a correct password clears both counters, which makes the check a
 * single atomic INCR instead of a read-then-write that parallel requests could slip past.
 * Keys hold a hash of the normalised email, never the address itself, and are counted the same
 * whether or not the account exists, so a 429 reveals nothing about who is registered.
 */
@Service
public class AccountThrottle {

    static final int LOGIN_PER_ACCOUNT_AND_IP = 5;
    static final Duration LOGIN_PER_ACCOUNT_AND_IP_WINDOW = Duration.ofMinutes(15);
    static final int LOGIN_PER_ACCOUNT = 20;
    static final Duration LOGIN_PER_ACCOUNT_WINDOW = Duration.ofHours(1);
    static final int RESET_EMAILS_PER_ACCOUNT = 5;
    static final Duration RESET_EMAILS_WINDOW = Duration.ofHours(1);

    private final RateLimiter rateLimiter;
    private final boolean enabled;

    public AccountThrottle(RateLimiter rateLimiter, @Value("${app.rate-limit.enabled}") boolean enabled) {
        this.rateLimiter = rateLimiter;
        this.enabled = enabled;
    }

    /** Counts a login attempt; throws before the password is checked when either layer is over. */
    public void checkLogin(String email, String clientIp) {
        if (!enabled) {
            return;
        }
        String account = accountKey(email);
        // The pair is checked first and a blocked pair never reaches the account counter, so one
        // address can't push a real user's account over its limit.
        RateLimiter.Result pair = rateLimiter.hit("login:" + account + ":" + clientIp,
                LOGIN_PER_ACCOUNT_AND_IP, LOGIN_PER_ACCOUNT_AND_IP_WINDOW);
        if (!pair.allowed()) {
            throw new RateLimitExceededException(pair.retryAfterSeconds());
        }
        RateLimiter.Result total = rateLimiter.hit("login:" + account, LOGIN_PER_ACCOUNT, LOGIN_PER_ACCOUNT_WINDOW);
        if (!total.allowed()) {
            throw new RateLimitExceededException(total.retryAfterSeconds());
        }
    }

    public void loginSucceeded(String email, String clientIp) {
        String account = accountKey(email);
        rateLimiter.reset("login:" + account + ":" + clientIp);
        rateLimiter.reset("login:" + account);
    }

    /** After a password reset: the owner proved control of the inbox, so a lockout no longer applies. */
    public void clearLogin(String email) {
        rateLimiter.reset("login:" + accountKey(email));
    }

    /**
     * Whether another reset email may go to this address. Over the limit the request is dropped
     * silently, because forgot-password answers 200 either way.
     */
    public boolean allowResetEmail(String email) {
        return !enabled || rateLimiter.hit("reset_email:" + accountKey(email),
                RESET_EMAILS_PER_ACCOUNT, RESET_EMAILS_WINDOW).allowed();
    }

    private static String accountKey(String email) {
        return OpaqueTokens.sha256(UserService.normalizeEmail(email));
    }
}
