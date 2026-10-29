package com.deliverease.service;

import com.deliverease.model.OrderStatus;
import com.deliverease.model.Role;
import com.deliverease.repository.*;
import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DashboardService {
    private final OrderRepository orders;
    private final UserRepository users;
    private final PartnerRepository partners;

    public DashboardService(OrderRepository orders, UserRepository users, PartnerRepository partners) {
        this.orders = orders;
        this.users = users;
        this.partners = partners;
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalOrders", orders.count());
        for (OrderStatus s : OrderStatus.values()) m.put(s.name(), orders.countByStatus(s));
        m.put("totalCustomers", users.countByRole(Role.CUSTOMER));
        m.put("activePartners", partners.countByActiveTrue());
        m.put("availablePartners", partners.countByActiveTrueAndAvailableTrue());
        m.put("revenue", orders.totalRevenue());
        return m;
    }
}
