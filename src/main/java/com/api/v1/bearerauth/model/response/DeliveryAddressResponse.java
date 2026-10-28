package com.api.v1.bearerauth.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DeliveryAddressResponse {
    private String address;
    private String city;
    private String phone;
    private Double latitude;
    private Double longitude;
}
