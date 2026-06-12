// Authenticated 2FA management endpoints: enroll (setup), confirm (enable), and disable.
package com.iloveshopping.auth;

import com.iloveshopping.auth.dto.TwoFactorDisableRequest;
import com.iloveshopping.auth.dto.TwoFactorEnableRequest;
import com.iloveshopping.auth.dto.TwoFactorEnableResponse;
import com.iloveshopping.auth.dto.TwoFactorSetupResponse;
import com.iloveshopping.user.User;
import com.iloveshopping.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/2fa")
public class TwoFactorController {

    private final TwoFactorService twoFactorService;
    private final UserService userService;

    public TwoFactorController(TwoFactorService twoFactorService, UserService userService) {
        this.twoFactorService = twoFactorService;
        this.userService = userService;
    }

    @PostMapping("/setup")
    public ResponseEntity<TwoFactorSetupResponse> setup(@AuthenticationPrincipal AuthPrincipal principal) {
        User user = userService.getById(principal.userId());
        return ResponseEntity.ok(twoFactorService.setup(user));
    }

    @PostMapping("/enable")
    public ResponseEntity<TwoFactorEnableResponse> enable(@AuthenticationPrincipal AuthPrincipal principal,
                                                          @Valid @RequestBody TwoFactorEnableRequest request) {
        User user = userService.getById(principal.userId());
        return ResponseEntity.ok(new TwoFactorEnableResponse(twoFactorService.enable(user, request.code())));
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disable(@AuthenticationPrincipal AuthPrincipal principal,
                                        @Valid @RequestBody TwoFactorDisableRequest request) {
        User user = userService.getById(principal.userId());
        twoFactorService.disable(user, request.password());
        return ResponseEntity.noContent().build();
    }
}
