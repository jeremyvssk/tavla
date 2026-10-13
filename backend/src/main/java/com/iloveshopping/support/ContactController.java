// POST /support/messages: the contact form. Public, so a shopper without an account can write too.
package com.iloveshopping.support;

import com.iloveshopping.support.dto.ContactRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    /** 202: taken, and queued for the inbox; nothing is stored, so there is nothing to return. */
    @PostMapping("/support/messages")
    public ResponseEntity<Void> send(@Valid @RequestBody ContactRequest request) {
        contactService.send(request);
        return ResponseEntity.accepted().build();
    }
}
