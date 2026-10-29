package com.deliverease.service;

import com.deliverease.dto.Dtos.*;
import com.deliverease.exception.AppException;
import com.deliverease.model.*;
import com.deliverease.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Transactional
public class OrderService {
    /** Statuses in which a delivery partner is considered busy. */
    private static final List<OrderStatus> ACTIVE =
            List.of(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP, OrderStatus.OUT_FOR_DELIVERY);
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orders;
    private final UserRepository users;
    private final PartnerRepository partners;
    private final StatusLogRepository logs;

    public OrderService(OrderRepository orders, UserRepository users,
                        PartnerRepository partners, StatusLogRepository logs) {
        this.orders = orders;
        this.users = users;
        this.partners = partners;
        this.logs = logs;
    }

    // ---------------------------------------------------------------- place
    public OrderResponse place(String email, OrderRequest r) {
        User customer = user(email);
        Order o = new Order();
        o.setTrackingId(newTrackingId());
        o.setCustomer(customer);
        o.setPickupAddress(r.pickupAddress().trim());
        o.setDropAddress(r.dropAddress().trim());
        o.setPackageDetails(r.packageDetails().trim());
        o.setWeightKg(r.weightKg());
        o.setDeliveryType(r.deliveryType());
        o.setCharge(calculateCharge(r.deliveryType(), r.weightKg()));
        o.setStatus(OrderStatus.PLACED);
        o.setDeliveryPin(String.format("%04d", RANDOM.nextInt(10000)));
        orders.save(o);
        log(o, OrderStatus.PLACED, "Order placed by customer");
        autoAssign(o);
        return toResponse(o, true);
    }

    /** Delivery charge = base fee of the delivery type + Rs. 10 per kg. */
    public static double calculateCharge(DeliveryType type, double weightKg) {
        double base = switch (type) {
            case STANDARD -> 40;
            case EXPRESS -> 80;
            case SAME_DAY -> 120;
        };
        return base + 10 * weightKg;
    }

    private String newTrackingId() {
        String id;
        do {
            StringBuilder sb = new StringBuilder("DE").append(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
            for (int i = 0; i < 5; i++) sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
            id = sb.toString();
        } while (orders.existsByTrackingId(id));
        return id;
    }

    // ----------------------------------------------------------- assignment
    private void autoAssign(Order o) {
        for (DeliveryPartner p : partners.findByActiveTrueAndAvailableTrue()) {
            if (!orders.existsByPartnerAndStatusIn(p, ACTIVE)) {
                assign(o, p);
                return;
            }
        }   // no free partner: order stays PLACED until one becomes free
    }

    /** Core business rule: an order is never assigned to a partner who already has an active delivery. */
    private void assign(Order o, DeliveryPartner p) {
        if (o.getStatus() != OrderStatus.PLACED) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Only orders in PLACED status can be assigned");
        }
        if (!p.isActive()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Delivery partner is deactivated");
        }
        if (orders.existsByPartnerAndStatusIn(p, ACTIVE)) {
            throw new AppException(HttpStatus.CONFLICT, p.getName() + " already has an active delivery");
        }
        o.setPartner(p);
        o.setStatus(OrderStatus.ASSIGNED);
        orders.save(o);
        p.setAvailable(false);
        partners.save(p);
        log(o, OrderStatus.ASSIGNED, "Assigned to " + p.getName());
    }

    public OrderResponse assignManually(Long orderId, Long partnerId) {
        Order o = find(orderId);
        DeliveryPartner p = partners.findById(partnerId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Delivery partner not found"));
        assign(o, p);
        return toResponse(o, true);
    }

    /** Frees a partner and immediately gives them the oldest waiting order, if any. */
    private void releasePartner(DeliveryPartner p) {
        p.setAvailable(true);
        partners.save(p);
        if (p.isActive()) {
            orders.findFirstByStatusOrderByCreatedAtAsc(OrderStatus.PLACED).ifPresent(w -> assign(w, p));
        }
    }

    // --------------------------------------------------------- status flow
    public OrderResponse updateStatus(Long id, String email, StatusUpdate u) {
        Order o = find(id);
        DeliveryPartner p = o.getPartner();
        if (p == null || p.getUser() == null || !p.getUser().getEmail().equals(email)) {
            throw new AppException(HttpStatus.FORBIDDEN, "This order is not assigned to you");
        }
        OrderStatus cur = o.getStatus(), next = u.status();
        boolean valid = (cur == OrderStatus.ASSIGNED && next == OrderStatus.PICKED_UP)
                || (cur == OrderStatus.PICKED_UP && next == OrderStatus.OUT_FOR_DELIVERY)
                || (cur == OrderStatus.OUT_FOR_DELIVERY && next == OrderStatus.DELIVERED);
        if (!valid) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid status change: " + cur + " to " + next);
        }
        if (next == OrderStatus.DELIVERED && (u.pin() == null || !u.pin().trim().equals(o.getDeliveryPin()))) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Incorrect delivery PIN");
        }
        o.setStatus(next);
        orders.save(o);
        log(o, next, "Updated by " + p.getName());
        if (next == OrderStatus.DELIVERED) releasePartner(p);
        return toResponse(o, false);
    }

    public OrderResponse cancel(Long id, String email) {
        User u = user(email);
        Order o = find(id);
        boolean owner = o.getCustomer().getId().equals(u.getId());
        if (!owner && u.getRole() != Role.ADMIN) {
            throw new AppException(HttpStatus.FORBIDDEN, "You cannot cancel this order");
        }
        if (o.getStatus() != OrderStatus.PLACED && o.getStatus() != OrderStatus.ASSIGNED) {
            throw new AppException(HttpStatus.BAD_REQUEST, "An order cannot be cancelled after it is picked up");
        }
        boolean wasAssigned = o.getStatus() == OrderStatus.ASSIGNED;
        o.setStatus(OrderStatus.CANCELLED);
        orders.save(o);
        log(o, OrderStatus.CANCELLED, "Cancelled by " + (owner ? "customer" : "admin"));
        if (wasAssigned) releasePartner(o.getPartner());
        return toResponse(o, true);
    }

    // -------------------------------------------------------------- queries
    @Transactional(readOnly = true)
    public List<OrderResponse> list(String email, String status) {
        User u = user(email);
        List<Order> list;
        switch (u.getRole()) {
            case ADMIN -> list = (status == null || status.isBlank())
                    ? orders.findAllByOrderByCreatedAtDesc()
                    : orders.findByStatusOrderByCreatedAtDesc(OrderStatus.valueOf(status.toUpperCase()));
            case CUSTOMER -> list = orders.findByCustomerOrderByCreatedAtDesc(u);
            default -> list = partners.findByUser(u)
                    .map(orders::findByPartnerOrderByCreatedAtDesc).orElse(List.of());
        }
        boolean showPin = u.getRole() != Role.DELIVERY_PARTNER;   // the PIN is shared only with the customer
        return list.stream().map(o -> toResponse(o, showPin)).toList();
    }

    @Transactional(readOnly = true)
    public TrackResponse track(String trackingId) {
        Order o = orders.findByTrackingId(trackingId.trim().toUpperCase())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "No order found for this tracking ID"));
        List<HistoryItem> history = logs.findByOrderOrderByChangedAtAsc(o).stream()
                .map(l -> new HistoryItem(l.getStatus().name(), l.getNote(), l.getChangedAt())).toList();
        return new TrackResponse(o.getTrackingId(), o.getStatus().name(),
                o.getPartner() == null ? null : o.getPartner().getName(),
                o.getPickupAddress(), o.getDropAddress(), history);
    }

    // -------------------------------------------------------------- helpers
    private Order find(Long id) {
        return orders.findById(id).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private User user(String email) {
        return users.findByEmail(email).orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private void log(Order o, OrderStatus s, String note) {
        StatusLog l = new StatusLog();
        l.setOrder(o);
        l.setStatus(s);
        l.setNote(note);
        logs.save(l);
    }

    private OrderResponse toResponse(Order o, boolean showPin) {
        DeliveryPartner p = o.getPartner();
        return new OrderResponse(o.getId(), o.getTrackingId(), o.getCustomer().getName(), o.getPickupAddress(),
                o.getDropAddress(), o.getPackageDetails(), o.getWeightKg(), o.getDeliveryType(), o.getCharge(),
                o.getStatus(), p == null ? null : p.getId(), p == null ? null : p.getName(),
                showPin ? o.getDeliveryPin() : null, o.getCreatedAt());
    }
}
