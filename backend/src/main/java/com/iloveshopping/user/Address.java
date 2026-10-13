// JPA entity mapping "user_addresses": an address the shopper saved, encrypted like the addresses on orders.
package com.iloveshopping.user;

import com.iloveshopping.crypto.Encrypted;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_addresses")
@Getter
@Setter
public class Address {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String label;

    @Convert(converter = Encrypted.class)
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Convert(converter = Encrypted.class)
    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Convert(converter = Encrypted.class)
    @Column(nullable = false)
    private String street;

    @Convert(converter = Encrypted.class)
    @Column
    private String flat;

    @Convert(converter = Encrypted.class)
    @Column(nullable = false)
    private String postcode;

    @Convert(converter = Encrypted.class)
    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String country;

    @Convert(converter = Encrypted.class)
    @Column(nullable = false)
    private String phone;

    @Column(name = "default_delivery", nullable = false)
    private boolean defaultDelivery;

    @Column(name = "default_billing", nullable = false)
    private boolean defaultBilling;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
