package com.api.v1.bearerauth.model.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DeliveryResponse {
    private Long id;
    private Long orderId;
    private Long driverUserId;
    private String driverName;
    private String status;
    private String deliveryAddress;
    private Double destinationLatitude;
    private Double destinationLongitude;
    private Double driverLatitude;
    private Double driverLongitude;
    private Double distanceKm;
    private Integer etaMinutes;
    private LocalDateTime assignedAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime lastLocationAt;
}
