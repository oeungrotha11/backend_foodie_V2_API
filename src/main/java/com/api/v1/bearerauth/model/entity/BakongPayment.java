package com.api.v1.bearerauth.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "bakong_payment", uniqueConstraints = {
    @UniqueConstraint(name = "uk_bakong_payment_md5", columnNames = "qr_md5"),
    @UniqueConstraint(name = "uk_bakong_payment_order", columnNames = "order_id")
})
public class BakongPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "qr_md5", nullable = false, length = 32)
    private String md5;

    @Column(name = "qr_payload", nullable = false, length = 4000)
    private String qrPayload;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime paidAt;
}
