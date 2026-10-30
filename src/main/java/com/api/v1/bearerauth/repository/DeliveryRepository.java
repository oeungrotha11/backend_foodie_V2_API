package com.api.v1.bearerauth.repository;

import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    Optional<Delivery> findByOrderOrderId(Long orderId);
    Optional<Delivery> findByOrderOrderIdAndOrderCustomerUser(Long orderId, AppUser user);
    Optional<Delivery> findFirstByDriverAndStatusInOrderByAssignedAtDesc(AppUser driver, java.util.Collection<String> statuses);
}
