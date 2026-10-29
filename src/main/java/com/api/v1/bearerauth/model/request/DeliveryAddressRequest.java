package com.api.v1.bearerauth.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeliveryAddressRequest {
    @NotBlank
    private String address;
    @NotBlank
    private String city;
    @NotBlank
    private String phone;
    private Double latitude;
    private Double longitude;
}
