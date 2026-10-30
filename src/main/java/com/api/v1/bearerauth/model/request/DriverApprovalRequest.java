package com.api.v1.bearerauth.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DriverApprovalRequest {
    @NotNull
    private Long userId;
    private String vehicleType;
    private String vehiclePlate;
}
