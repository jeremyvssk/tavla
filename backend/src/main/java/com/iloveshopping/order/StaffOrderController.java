// The staff's order endpoints: the queue and its export, one order, moving it along, notes, start-packing and refunds.
package com.iloveshopping.order;

import com.iloveshopping.csv.Csv;
import com.iloveshopping.order.dto.OrderNoteRequest;
import com.iloveshopping.order.dto.RefundRequest;
import com.iloveshopping.order.dto.StaffOrderDetailResponse;
import com.iloveshopping.order.dto.StaffOrderPageResponse;
import com.iloveshopping.order.dto.StaffOrderParams;
import com.iloveshopping.order.dto.StartPackingRequest;
import com.iloveshopping.order.dto.StatusMoveRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Support and admins only (SecurityConfig, StaffArea.ORDERS). */
@RestController
@RequestMapping("/staff/orders")
public class StaffOrderController {

    private final StaffOrderService staffOrderService;

    public StaffOrderController(StaffOrderService staffOrderService) {
        this.staffOrderService = staffOrderService;
    }

    @GetMapping
    public StaffOrderPageResponse list(@Valid @ModelAttribute StaffOrderParams params) {
        return staffOrderService.list(params);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@Valid @ModelAttribute StaffOrderParams params) {
        return Csv.download(staffOrderService.export(params), "tavla-orders.csv");
    }

    @GetMapping("/{id}")
    public StaffOrderDetailResponse detail(@PathVariable UUID id) {
        return staffOrderService.detail(id);
    }

    @PostMapping("/{id}/status")
    public StaffOrderDetailResponse move(@PathVariable UUID id, @Valid @RequestBody StatusMoveRequest request) {
        return staffOrderService.move(id, request);
    }

    @PostMapping("/{id}/notes")
    public StaffOrderDetailResponse note(@PathVariable UUID id, @Valid @RequestBody OrderNoteRequest request) {
        return staffOrderService.addNote(id, request.note());
    }

    @PostMapping("/start-packing")
    public StartPackingRequest.Result startPacking(@Valid @RequestBody StartPackingRequest request) {
        return staffOrderService.startPacking(request.ids());
    }

    @PostMapping("/{id}/refunds")
    public StaffOrderDetailResponse refund(@PathVariable UUID id, @Valid @RequestBody RefundRequest request) {
        return staffOrderService.refund(id, request);
    }
}
