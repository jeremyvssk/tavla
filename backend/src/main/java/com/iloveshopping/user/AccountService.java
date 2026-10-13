// The shopper's own account: read and change the profile, export everything kept about them, and delete it.
package com.iloveshopping.user;

import com.iloveshopping.auth.AccountThrottle;
import com.iloveshopping.auth.TokenStoreService;
import com.iloveshopping.auth.exception.AccessConflictException;
import com.iloveshopping.auth.exception.InvalidCredentialsException;
import com.iloveshopping.catalog.ReviewService;
import com.iloveshopping.order.OrderService;
import com.iloveshopping.user.dto.AccountExport;
import com.iloveshopping.user.dto.DeleteAccountRequest;
import com.iloveshopping.user.dto.ProfileResponse;
import com.iloveshopping.user.dto.ProfileUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

/**
 * Only ever the signed-in account: the id comes from the access token, never from the request.
 *
 * <p>Deleting removes the account, its addresses, cart, reviews and votes (ON DELETE CASCADE). Its orders
 * stay, because they are the shop's accounting records, but lose the link to the account (ON DELETE SET
 * NULL, V15), so nobody can open them from the shop any more. Every session ends at once.
 */
@Service
public class AccountService {

    private final UserRepository users;
    private final UserService userService;
    private final AddressService addressService;
    private final OrderService orderService;
    private final ReviewService reviewService;
    private final AccountThrottle accountThrottle;
    private final TokenStoreService tokenStore;

    public AccountService(UserRepository users, UserService userService, AddressService addressService, OrderService orderService,
                          ReviewService reviewService, AccountThrottle accountThrottle, TokenStoreService tokenStore) {
        this.users = users;
        this.userService = userService;
        this.addressService = addressService;
        this.orderService = orderService;
        this.reviewService = reviewService;
        this.accountThrottle = accountThrottle;
        this.tokenStore = tokenStore;
    }

    @Transactional(readOnly = true)
    public ProfileResponse profile(UUID userId) {
        return view(userService.getById(userId));
    }

    @Transactional
    public ProfileResponse update(UUID userId, ProfileUpdateRequest request) {
        User user = userService.getById(userId);
        user.setFullName(request.fullName().strip());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().strip());
        return view(users.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public AccountExport export(UUID userId) {
        User u = userService.getById(userId);
        return new AccountExport(Instant.now(),
                new AccountExport.Profile(u.getFullName(), u.getEmail(), u.getPhone(), u.getRole().name(), u.getAuthProvider().name(),
                        u.isTwoFactorEnabled(), u.getCreatedAt(), u.getLastSignInAt()),
                addressService.list(userId), orderService.allFor(userId), reviewService.allBy(userId));
    }

    /**
     * Asks for the password again (counted like a 2FA step-up, so a stolen session can't guess it), or for a
     * Google account, which has none, its own email typed out. A staff account is refused: an admin changes
     * its role back to customer first, so the shop can't lose its staff by one click.
     */
    @Transactional
    public void delete(UUID userId, DeleteAccountRequest request) {
        User user = userService.getById(userId);
        if (user.getRole().isStaff()) {
            throw new AccessConflictException("staff_account");
        }
        accountThrottle.checkStepUp(userId);
        boolean confirmed = user.getAuthProvider() == AuthProvider.LOCAL
                ? request.password() != null && userService.passwordMatches(user, request.password())
                : request.email() != null && UserService.normalizeEmail(request.email()).equals(user.getEmail());
        if (!confirmed) {
            throw new InvalidCredentialsException();
        }
        accountThrottle.stepUpSucceeded(userId);
        reviewService.forgetVotesOf(userId);
        users.delete(user);
        users.flush();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                tokenStore.revokeAllRefreshTokens(userId);
                tokenStore.revokeAccessTokens(userId);
            }
        });
    }

    private static ProfileResponse view(User u) {
        return new ProfileResponse(u.getFullName(), u.getEmail(), u.getPhone(), u.isEmailVerified(), u.getCreatedAt(), u.getUpdatedAt());
    }
}
