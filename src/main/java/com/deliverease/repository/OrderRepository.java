package com.deliverease.repository;

import com.deliverease.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByTrackingId(String trackingId);
    boolean existsByTrackingId(String trackingId);
    List<Order> findAllByOrderByCreatedAtDesc();
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);
    List<Order> findByCustomerOrderByCreatedAtDesc(User customer);
    List<Order> findByPartnerOrderByCreatedAtDesc(DeliveryPartner partner);
    Optional<Order> findFirstByStatusOrderByCreatedAtAsc(OrderStatus status);
    boolean existsByPartnerAndStatusIn(DeliveryPartner partner, Collection<OrderStatus> statuses);
    long countByStatus(OrderStatus status);

    @Query("select coalesce(sum(o.charge), 0.0) from Order o where o.status = com.deliverease.model.OrderStatus.DELIVERED")
    double totalRevenue();
}
