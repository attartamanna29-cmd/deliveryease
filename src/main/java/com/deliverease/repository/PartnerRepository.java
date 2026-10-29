package com.deliverease.repository;

import com.deliverease.model.DeliveryPartner;
import com.deliverease.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PartnerRepository extends JpaRepository<DeliveryPartner, Long> {
    Optional<DeliveryPartner> findByUser(User user);
    List<DeliveryPartner> findByActiveTrueAndAvailableTrue();
    long countByActiveTrue();
    long countByActiveTrueAndAvailableTrue();
}
