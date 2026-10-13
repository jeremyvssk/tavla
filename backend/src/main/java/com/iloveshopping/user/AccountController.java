// The signed-in shopper's own account: profile, data export, deletion and saved addresses.
package com.iloveshopping.user;

import com.iloveshopping.auth.AuthPrincipal;
import com.iloveshopping.user.dto.AccountExport;
import com.iloveshopping.user.dto.AddressDefaultRequest;
import com.iloveshopping.user.dto.AddressRequest;
import com.iloveshopping.user.dto.AddressResponse;
import com.iloveshopping.user.dto.DeleteAccountRequest;
import com.iloveshopping.user.dto.ProfileResponse;
import com.iloveshopping.user.dto.ProfileUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Every path is the caller's own account; the user id comes from the access token (anyRequest().authenticated()). */
@RestController
@RequestMapping("/users/me")
public class AccountController {

    private final AccountService accountService;
    private final AddressService addressService;

    public AccountController(AccountService accountService, AddressService addressService) {
        this.accountService = accountService;
        this.addressService = addressService;
    }

    @GetMapping
    public ProfileResponse profile(@AuthenticationPrincipal AuthPrincipal me) {
        return accountService.profile(me.userId());
    }

    @PatchMapping
    public ProfileResponse update(@AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody ProfileUpdateRequest request) {
        return accountService.update(me.userId(), request);
    }

    /** A file to save, never shown in the browser: the JSON is personal data. */
    @GetMapping("/export")
    public ResponseEntity<AccountExport> export(@AuthenticationPrincipal AuthPrincipal me) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("tavla-my-data.json").build().toString())
                .body(accountService.export(me.userId()));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody DeleteAccountRequest request) {
        accountService.delete(me.userId(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/addresses")
    public List<AddressResponse> addresses(@AuthenticationPrincipal AuthPrincipal me) {
        return addressService.list(me.userId());
    }

    @PostMapping("/addresses")
    public ResponseEntity<List<AddressResponse>> create(@AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(addressService.create(me.userId(), request));
    }

    @PutMapping("/addresses/{id}")
    public List<AddressResponse> update(@AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID id,
                                        @Valid @RequestBody AddressRequest request) {
        return addressService.update(me.userId(), id, request);
    }

    @DeleteMapping("/addresses/{id}")
    public List<AddressResponse> remove(@AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID id) {
        return addressService.remove(me.userId(), id);
    }

    @PutMapping("/addresses/{id}/default")
    public List<AddressResponse> makeDefault(@AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID id,
                                             @Valid @RequestBody AddressDefaultRequest request) {
        return addressService.makeDefault(me.userId(), id, request.kind());
    }
}
