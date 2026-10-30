package com.api.v1.bearerauth.model.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DriverLocationRequest {
    @NotNull
    @DecimalMin("-90.0") @DecimalMax("90.0")
    private Double latitude;
    @NotNull
    @DecimalMin("-180.0") @DecimalMax("180.0")
    private Double longitude;
    private Double distanceKm;
    private Integer etaMinutes;
}
