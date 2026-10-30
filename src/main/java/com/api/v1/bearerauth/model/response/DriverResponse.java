package com.api.v1.bearerauth.model.response;

import lombok.Data;

@Data
public class DriverResponse {
    private Long userId;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String vehicleType;
    private String vehiclePlate;
    private boolean approved;
    private boolean available;
}
