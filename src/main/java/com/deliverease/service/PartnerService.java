package com.deliverease.service;

import com.deliverease.dto.Dtos.*;
import com.deliverease.exception.AppException;
import com.deliverease.model.*;
import com.deliverease.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class PartnerService {
    private static final List<OrderStatus> ACTIVE =
            List.of(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP, OrderStatus.OUT_FOR_DELIVERY);

    private final PartnerRepository partners;
    private final UserRepository users;
    private final OrderRepository orders;
    private final PasswordEncoder encoder;

    public PartnerService(PartnerRepository partners, UserRepository users,
                          OrderRepository orders, PasswordEncoder encoder) {
        this.partners = partners;
        this.users = users;
        this.orders = orders;
        this.encoder = encoder;
    }

    @Transactional
    public PartnerResponse create(PartnerRequest r) {
        if (r.email() == null || r.email().isBlank() || r.password() == null || r.password().length() < 6) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Login email and a password of at least 6 characters are required");
        }
        String email = r.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "An account with this email already exists");
        }
        User u = new User();
        u.setName(r.name());
        u.setEmail(email);
        u.setPassword(encoder.encode(r.password()));
        u.setPhone(r.phone());
        u.setRole(Role.DELIVERY_PARTNER);
        users.save(u);

        DeliveryPartner p = new DeliveryPartner();
        p.setName(r.name());
        p.setPhone(r.phone());
        p.setVehicleType(r.vehicleType());
        p.setUser(u);
        return toResponse(partners.save(p));
    }

    public List<PartnerResponse> list() {
        return partners.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public PartnerResponse update(Long id, PartnerRequest r) {
        DeliveryPartner p = find(id);
        p.setName(r.name());
        p.setPhone(r.phone());
        p.setVehicleType(r.vehicleType());
        return toResponse(partners.save(p));
    }

    @Transactional
    public PartnerResponse setActive(Long id, boolean active) {
        DeliveryPartner p = find(id);
        if (!active && orders.existsByPartnerAndStatusIn(p, ACTIVE)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Cannot deactivate a partner with an active delivery");
        }
        p.setActive(active);
        return toResponse(partners.save(p));
    }

    private DeliveryPartner find(Long id) {
        return partners.findById(id).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Delivery partner not found"));
    }

    private PartnerResponse toResponse(DeliveryPartner p) {
        return new PartnerResponse(p.getId(), p.getName(), p.getPhone(), p.getVehicleType(),
                p.isAvailable(), p.isActive(), p.getUser() == null ? null : p.getUser().getEmail());
    }
}
