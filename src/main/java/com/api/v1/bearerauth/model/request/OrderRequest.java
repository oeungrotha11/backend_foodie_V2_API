package com.api.v1.bearerauth.model.request;

import com.api.v1.bearerauth.model.enumerate.OrderType;
import com.api.v1.bearerauth.model.enumerate.PaymentMethod;
import jakarta.validation.Valid;
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

    // Optional: the customer may select one reward from their wallet at checkout.
    @Positive(message = "Reward ID must be positive")
    private Long rewardId;

    @NotEmpty(message = "Order must contain at least one product")
    @Valid
    private List<OrderItemRequest> items;
}
