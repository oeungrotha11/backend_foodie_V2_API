package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.common.response.SuccessResponse;
import com.api.v1.bearerauth.model.entity.*;
import com.api.v1.bearerauth.model.response.DeliveryResponse;
import com.api.v1.bearerauth.repository.DeliveryRepository;
import com.api.v1.bearerauth.security.CurrentUserService;
import com.api.v1.bearerauth.util.ApiResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer/deliveries")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
public class CustomerDeliveryController {
    private final DeliveryRepository deliveries;
    private final CurrentUserService currentUser;

    @GetMapping("/order/{orderId}")
    @Transactional(readOnly = true)
    public ResponseEntity<SuccessResponse<DeliveryResponse>> track(@PathVariable Long orderId) {
        AppUser user = currentUser.getCurrentUser();
        Delivery delivery = deliveries.findByOrderOrderIdAndOrderCustomerUser(orderId, user)
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, "Delivery not found for this order"));
        DeliveryResponse r = new DeliveryResponse();
        r.setId(delivery.getId());
        r.setOrderId(delivery.getOrder().getOrderId());
        r.setDriverUserId(delivery.getDriver().getId());
        r.setDriverName(delivery.getDriver().getEmail());
        r.setStatus(delivery.getStatus());
        r.setDeliveryAddress(delivery.getOrder().getDeliveryAddress());
        r.setDestinationLatitude(delivery.getOrder().getDeliveryLatitude());
        r.setDestinationLongitude(delivery.getOrder().getDeliveryLongitude());
        r.setDriverLatitude(delivery.getDriverLatitude());
        r.setDriverLongitude(delivery.getDriverLongitude());
        r.setDistanceKm(delivery.getDistanceKm());
        r.setEtaMinutes(delivery.getEtaMinutes());
        r.setAssignedAt(delivery.getAssignedAt());
        r.setPickedUpAt(delivery.getPickedUpAt());
        r.setDeliveredAt(delivery.getDeliveredAt());
        r.setLastLocationAt(delivery.getLastLocationAt());
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, r));
    }
}
