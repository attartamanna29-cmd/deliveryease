package com.deliverease.repository;

import com.deliverease.model.Order;
import com.deliverease.model.StatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StatusLogRepository extends JpaRepository<StatusLog, Long> {
    List<StatusLog> findByOrderOrderByChangedAtAsc(Order order);
}
