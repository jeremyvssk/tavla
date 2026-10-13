// Delivery endpoints: the checkout's list of options, and the staff's delivery settings.
package com.iloveshopping.delivery;

import com.iloveshopping.delivery.dto.DeliveryOptionRequest;
import com.iloveshopping.delivery.dto.DeliveryOrderRequest;
import com.iloveshopping.delivery.dto.DeliverySettingsResponse;
import com.iloveshopping.delivery.dto.FreeShippingRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** GET /delivery-options is public (the checkout); every /staff/delivery path is Sales and admins (StaffArea.DELIVERY). */
@RestController
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping("/delivery-options")
    public DeliverySettingsResponse shop() {
        return deliveryService.shopView();
    }

    @GetMapping("/staff/delivery")
    public DeliverySettingsResponse staff() {
        return deliveryService.staffView();
    }

    @PutMapping("/staff/delivery/free-shipping")
    public DeliverySettingsResponse freeShipping(@Valid @RequestBody FreeShippingRequest request) {
        return deliveryService.saveFreeShipping(request);
    }

    @PostMapping("/staff/delivery/options")
    public ResponseEntity<DeliverySettingsResponse> create(@Valid @RequestBody DeliveryOptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deliveryService.create(request));
    }

    @PutMapping("/staff/delivery/options/{id}")
    public DeliverySettingsResponse update(@PathVariable String id, @Valid @RequestBody DeliveryOptionRequest request) {
        return deliveryService.update(id, request);
    }

    @PutMapping("/staff/delivery/order")
    public DeliverySettingsResponse reorder(@Valid @RequestBody DeliveryOrderRequest request) {
        return deliveryService.reorder(request.ids());
    }
}
