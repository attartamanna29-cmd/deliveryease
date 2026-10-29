package com.deliverease.controller;

import com.deliverease.dto.Dtos.TrackResponse;
import com.deliverease.service.OrderService;
import org.springframework.web.bind.annotation.*;

/** Public endpoint: anyone can check an order's status using its tracking ID. */
@RestController
@RequestMapping("/api/track")
public class TrackController {
    private final OrderService service;

    public TrackController(OrderService service) { this.service = service; }

    @GetMapping("/{trackingId}")
    public TrackResponse track(@PathVariable String trackingId) { return service.track(trackingId); }
}
