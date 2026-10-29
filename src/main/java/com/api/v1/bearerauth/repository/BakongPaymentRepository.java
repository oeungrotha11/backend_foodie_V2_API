package com.api.v1.bearerauth.repository;

import com.api.v1.bearerauth.model.entity.BakongPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BakongPaymentRepository extends JpaRepository<BakongPayment, Long> {
    Optional<BakongPayment> findByMd5IgnoreCase(String md5);
    Optional<BakongPayment> findByOrderId(Long orderId);
}
