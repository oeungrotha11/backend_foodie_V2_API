package com.api.v1.bearerauth.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminCreateDriverRequest {
    @Email @NotBlank
    private String email;
    @NotBlank @Size(min = 8, max = 100)
    private String password;
    @NotBlank
    private String fullName;
    private String phoneNumber;
    private String vehicleType;
    private String vehiclePlate;
}
