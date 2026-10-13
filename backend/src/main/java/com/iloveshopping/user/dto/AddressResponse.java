// A saved address as the account pages show it.
package com.iloveshopping.user.dto;

import java.util.UUID;

public record AddressResponse(UUID id, String label, String firstName, String lastName, String street, String flat, String postcode,
                              String city, String country, String phone, boolean defaultDelivery, boolean defaultBilling) {
}
