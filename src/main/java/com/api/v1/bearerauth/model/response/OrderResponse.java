package com.api.v1.bearerauth.model.response;

import com.api.v1.bearerauth.model.enumerate.OrderStatus;
import com.api.v1.bearerauth.model.enumerate.OrderType;
import com.api.v1.bearerauth.model.enumerate.PaymentMethod;
import com.api.v1.bearerauth.model.enumerate.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;


@Data
public class OrderResponse {
    @JsonProperty("id")
    private Long orderId;

    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime orderDate;

    private OrderStatus status;
    private OrderType orderType;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;

    @JsonProperty("address")
    private String deliveryAddress;

    private Double deliveryLatitude;
    private Double deliveryLongitude;

    private Double subtotal;
    private Double deliveryFee;

    @JsonProperty("total")
    private Double totalAmount;

    @JsonProperty("itemCount")
    private Integer totalItems;

    private Long customerId;
    private Long rewardId;
    private String rewardTitle;
    private Double discount;
    private List<OrderItemResponse> items;
}
