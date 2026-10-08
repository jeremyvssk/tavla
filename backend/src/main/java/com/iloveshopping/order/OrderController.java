// Order endpoints: place an order, read it, pay it again after a decline, cancel it, and the account's order list.
package com.iloveshopping.order;

import com.iloveshopping.auth.AuthPrincipal;
import com.iloveshopping.cart.CartOwner;
import com.iloveshopping.order.dto.CancelOrderRequest;
import com.iloveshopping.order.dto.DeliveryDetailsResponse;
import com.iloveshopping.order.dto.OrderDetailResponse;
import com.iloveshopping.order.dto.OrderListParams;
import com.iloveshopping.order.dto.OrderListResponse;
import com.iloveshopping.order.dto.PaymentStartResponse;
import com.iloveshopping.order.dto.PlaceOrderRequest;
import com.iloveshopping.order.dto.PlacedOrderResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public like the cart, because guests check out too. Whose order it is comes from the same place as
 * whose cart: the access token, or the guest's cart cookie. Someone else's order answers 404. The
 * order list and the last delivery details belong to an account, so those two need a login.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<PlacedOrderResponse> place(@AuthenticationPrincipal AuthPrincipal principal, HttpServletRequest request,
                                                     @Valid @RequestBody PlaceOrderRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.place(CartOwner.of(principal, request), body));
    }

    @GetMapping("/{orderId}")
    public OrderDetailResponse get(@AuthenticationPrincipal AuthPrincipal principal, HttpServletRequest request,
                                   @PathVariable UUID orderId) {
        return orderService.get(orderId, CartOwner.of(principal, request));
    }

    @PostMapping("/{orderId}/payment")
    public PaymentStartResponse startPayment(@AuthenticationPrincipal AuthPrincipal principal, HttpServletRequest request,
                                             @PathVariable UUID orderId) {
        return orderService.startPayment(orderId, CartOwner.of(principal, request));
    }

    /** 200 with the cancelled order; 409 order_not_cancellable once packing has started, or before it was paid. */
    @PostMapping("/{orderId}/cancel")
    public OrderDetailResponse cancel(@AuthenticationPrincipal AuthPrincipal principal, HttpServletRequest request,
                                      @PathVariable UUID orderId, @Valid @RequestBody(required = false) CancelOrderRequest body) {
        return orderService.cancel(orderId, CartOwner.of(principal, request), body == null ? null : body.reason());
    }

    @GetMapping
    public OrderListResponse list(@AuthenticationPrincipal AuthPrincipal principal, @Valid @ModelAttribute OrderListParams params) {
        return orderService.list(principal.userId(), params);
    }

    /** 204 before the account's first order. */
    @GetMapping("/last-delivery")
    public ResponseEntity<DeliveryDetailsResponse> lastDelivery(@AuthenticationPrincipal AuthPrincipal principal) {
        return orderService.lastDelivery(principal.userId()).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
