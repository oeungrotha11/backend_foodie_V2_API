package com.api.v1.bearerauth.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeliveryAssignmentRequest {
    @NotNull
    private Long orderId;
    @NotNull
    private Long driverUserId;
}
