package com.deliverease.controller;

import com.deliverease.dto.Dtos.*;
import com.deliverease.service.PartnerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Admin-only (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/partners")
public class PartnerController {
    private final PartnerService service;

    public PartnerController(PartnerService service) { this.service = service; }

    @PostMapping
    public PartnerResponse create(@Valid @RequestBody PartnerRequest r) { return service.create(r); }

    @GetMapping
    public List<PartnerResponse> list() { return service.list(); }

    @PutMapping("/{id}")
    public PartnerResponse update(@PathVariable Long id, @Valid @RequestBody PartnerRequest r) {
        return service.update(id, r);
    }

    @PutMapping("/{id}/active")
    public PartnerResponse setActive(@PathVariable Long id, @RequestParam boolean value) {
        return service.setActive(id, value);
    }
}
