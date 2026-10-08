// Body of GET /orders/last-delivery: the contact and address of the account's latest order, to prefill the checkout.
package com.iloveshopping.order.dto;

/** The fields have the checkout form's names, so the page can fill its blanks one to one. */
public record DeliveryDetailsResponse(String email, String phone, String firstName, String lastName, String street,
                                      String flat, String postcode, String city, String country) {
}
