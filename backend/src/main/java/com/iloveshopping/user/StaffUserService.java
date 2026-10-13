// The admin's view of accounts: list and find them, and change a role with the changer's own 2FA code.
package com.iloveshopping.user;

import com.iloveshopping.audit.AuditEvent;
import com.iloveshopping.audit.AuditKind;
import com.iloveshopping.audit.AuditLog;
import com.iloveshopping.audit.AuditTarget;
import com.iloveshopping.auth.AccountThrottle;
import com.iloveshopping.auth.TokenStoreService;
import com.iloveshopping.auth.TwoFactorService;
import com.iloveshopping.auth.exception.AccessConflictException;
import com.iloveshopping.auth.exception.InvalidTwoFactorCodeException;
import com.iloveshopping.order.OrderService;
import com.iloveshopping.user.dto.StaffUserPageResponse;
import com.iloveshopping.user.dto.StaffUserResponse;
import com.iloveshopping.user.dto.UserListParams;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The role rules, all checked here on the server:
 * <ul>
 *   <li>Every change needs the changer's current code from their authenticator app, so a stolen session
 *       alone can't hand out rights. Wrong codes are limited like sign-in attempts.</li>
 *   <li>Nobody changes their own role.</li>
 *   <li>Admin is never granted or taken away here, only from the server's command line (README,
 *       "Becoming an admin"). Even a fully taken-over admin account can't make more admins or lock the
 *       real ones out, and "the last admin was demoted" can't happen.</li>
 *   <li>Only a password account can be staff: staff need 2FA, and 2FA is set up with the password.</li>
 * </ul>
 * A changed role takes effect at once: every access token the person holds is refused after the commit,
 * and their next refresh carries the new role.
 */
@Service
public class StaffUserService {

    private final UserRepository users;
    private final UserService userService;
    private final OrderService orderService;
    private final TwoFactorService twoFactorService;
    private final AccountThrottle accountThrottle;
    private final TokenStoreService tokenStore;
    private final AuditLog auditLog;

    public StaffUserService(UserRepository users, UserService userService, OrderService orderService,
                            TwoFactorService twoFactorService, AccountThrottle accountThrottle,
                            TokenStoreService tokenStore, AuditLog auditLog) {
        this.users = users;
        this.userService = userService;
        this.orderService = orderService;
        this.twoFactorService = twoFactorService;
        this.accountThrottle = accountThrottle;
        this.tokenStore = tokenStore;
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public StaffUserPageResponse list(UserListParams params) {
        int page = params.page() == null ? 0 : params.page();
        int size = params.size() == null ? 25 : params.size();
        Page<User> found = users.findAll(filter(params),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));
        Map<UUID, Long> orders = orderService.orderCounts(found.getContent().stream().map(User::getId).toList());
        List<StaffUserResponse> items = found.getContent().stream()
                .map(u -> view(u, orders.getOrDefault(u.getId(), 0L))).toList();
        return new StaffUserPageResponse(items, page, size, found.getTotalElements(), found.getTotalPages(), counts());
    }

    @Transactional
    public StaffUserResponse changeRole(UUID changerId, UUID targetId, Role role, String code) {
        accountThrottle.checkStepUp(changerId);
        User changer = userService.getById(changerId);
        if (!twoFactorService.verifyAppCode(changer, code)) {
            throw new InvalidTwoFactorCodeException();
        }
        accountThrottle.stepUpSucceeded(changerId);
        User target = userService.getById(targetId);
        if (target.getId().equals(changerId)) {
            throw new AccessConflictException("own_role");
        }
        if (target.getRole() == Role.ADMIN || role == Role.ADMIN) {
            throw new AccessConflictException("admin_role_locked");
        }
        if (role.isStaff() && target.getAuthProvider() != AuthProvider.LOCAL) {
            throw new AccessConflictException("staff_needs_password_account");
        }
        Role before = target.getRole();
        if (before != role) {
            target.setRole(role);
            users.save(target);
            auditLog.record(AuditEvent.of(AuditKind.ACCESS, "Changed {target}'s role")
                    .target(AuditTarget.USER, target.getId(), null).change(name(before), name(role)));
            // after the commit: a refresh in between would otherwise still read the old role
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tokenStore.revokeAccessTokens(targetId);
                }
            });
        }
        return view(target, orderService.orderCounts(List.of(targetId)).getOrDefault(targetId, 0L));
    }

    private Specification<User> filter(UserListParams params) {
        Specification<User> spec = switch (params.group() == null ? "staff" : params.group()) {
            case "customers" -> (root, query, cb) -> cb.equal(root.get("role"), Role.CUSTOMER);
            case "all" -> Specification.unrestricted();
            default -> (root, query, cb) -> cb.notEqual(root.get("role"), Role.CUSTOMER);
        };
        if ("off".equals(params.twoFactor())) {
            spec = spec.and((root, query, cb) -> cb.isFalse(root.get("twoFactorEnabled")));
        }
        if (params.q() != null && !params.q().isBlank()) {
            // The email is ciphertext (V16), so the search is the whole address matched by its blind index,
            // not the start of one: the price of encrypting it.
            String hash = userService.emailHash(params.q());
            spec = spec.and((root, query, cb) -> cb.equal(root.get("emailHash"), hash));
        }
        return spec;
    }

    private Map<String, Long> counts() {
        long all = 0;
        long customers = 0;
        long admins = 0;
        for (Object[] row : users.countByRole()) {
            long n = ((Number) row[1]).longValue();
            all += n;
            if (Role.CUSTOMER.name().equals(row[0])) {
                customers = n;
            } else if (Role.ADMIN.name().equals(row[0])) {
                admins = n;
            }
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("all", all);
        counts.put("customers", customers);
        counts.put("staff", all - customers);
        counts.put("admins", admins);
        return counts;
    }

    private static StaffUserResponse view(User u, long orderCount) {
        return new StaffUserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.isTwoFactorEnabled(),
                u.getCreatedAt(), u.getLastSignInAt(), orderCount);
    }

    private static String name(Role role) {
        return role.name().charAt(0) + role.name().substring(1).toLowerCase(Locale.ROOT);
    }
}
