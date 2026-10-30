package com.api.v1.bearerauth.model.request;

import com.api.v1.bearerauth.model.enumerate.OrderType;
import com.api.v1.bearerauth.model.enumerate.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class OrderRequest {
    @NotNull(message = "Order type is required")
    private OrderType orderType;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    private String deliveryAddress;

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private Double deliveryLatitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private Double deliveryLongitude;

    // Optional: the customer may select one reward from their wallet at checkout.
    @Positive(message = "Reward ID must be positive")
    private Long rewardId;

    @NotEmpty(message = "Order must contain at least one product")
    @Valid
    private List<OrderItemRequest> items;
}
