// Admin endpoints for accounts: the users list, and changing a role.
package com.iloveshopping.user;

import com.iloveshopping.auth.AuthPrincipal;
import com.iloveshopping.user.dto.RoleChangeRequest;
import com.iloveshopping.user.dto.StaffUserPageResponse;
import com.iloveshopping.user.dto.StaffUserResponse;
import com.iloveshopping.user.dto.UserListParams;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Admin only, with a second factor (SecurityConfig, StaffArea.USERS). The role rules are in StaffUserService. */
@RestController
@RequestMapping("/staff/users")
public class StaffUserController {

    private final StaffUserService staffUserService;

    public StaffUserController(StaffUserService staffUserService) {
        this.staffUserService = staffUserService;
    }

    @GetMapping
    public StaffUserPageResponse list(@Valid @ModelAttribute UserListParams params) {
        return staffUserService.list(params);
    }

    /** 401 invalid_2fa_code for a wrong code; 409 own_role, admin_role_locked or staff_needs_password_account. */
    @PutMapping("/{userId}/role")
    public StaffUserResponse changeRole(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID userId,
                                        @Valid @RequestBody RoleChangeRequest request) {
        return staffUserService.changeRole(principal.userId(), userId, request.role(), request.code());
    }
}
