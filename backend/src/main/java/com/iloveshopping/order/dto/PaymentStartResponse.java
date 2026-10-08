// Body of POST /orders/{id}/payment: the client secret for paying the order again with another card.
package com.iloveshopping.order.dto;

public record PaymentStartResponse(String clientSecret) {
}
