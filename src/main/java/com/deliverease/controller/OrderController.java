package com.deliverease.controller;

import com.deliverease.dto.Dtos.*;
import com.deliverease.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) { this.service = service; }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public OrderResponse place(Authentication a, @Valid @RequestBody OrderRequest r) {
        return service.place(a.getName(), r);
    }

    /** Admin: all orders (optional status filter); Customer: own orders; Partner: assigned orders. */
    @GetMapping
    public List<OrderResponse> list(Authentication a, @RequestParam(required = false) String status) {
        return service.list(a.getName(), status);
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse assign(@PathVariable Long id, @RequestParam Long partnerId) {
        return service.assignManually(id, partnerId);
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public OrderResponse cancel(@PathVariable Long id, Authentication a) {
        return service.cancel(id, a.getName());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    public OrderResponse updateStatus(@PathVariable Long id, Authentication a, @Valid @RequestBody StatusUpdate u) {
        return service.updateStatus(id, a.getName(), u);
    }
}
