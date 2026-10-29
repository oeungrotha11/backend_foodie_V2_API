package com.api.v1.bearerauth.model.entity;

import com.api.v1.bearerauth.model.enumerate.OrderStatus;
import com.api.v1.bearerauth.model.enumerate.OrderType;
import com.api.v1.bearerauth.model.enumerate.PaymentStatus;
import com.api.v1.bearerauth.model.enumerate.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "order_tb")
public class OrderTb {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;

    @Column(nullable = false)
    private LocalDateTime orderDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'UNPAID'")
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(length = 500)
    private String deliveryAddress;

    @Column(name = "delivery_latitude")
    private Double deliveryLatitude;

    @Column(name = "delivery_longitude")
    private Double deliveryLongitude;

    @Column(nullable = false)
    private Double deliveryFee;

    @Column(nullable = false)
    private Double totalAmount;

    @Column(length = 50)
    private String promoCode;

    @Column(nullable = false)
    private Double discount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<ProductOrder> items = new ArrayList<>();
}
