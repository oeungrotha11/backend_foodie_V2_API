package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.common.response.SuccessResponse;
import com.api.v1.bearerauth.model.entity.*;
import com.api.v1.bearerauth.model.request.*;
import com.api.v1.bearerauth.model.response.DeliveryResponse;
import com.api.v1.bearerauth.repository.*;
import com.api.v1.bearerauth.security.CurrentUserService;
import com.api.v1.bearerauth.util.ApiResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/driver")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DRIVER')")
public class DriverDeliveryController {
    private static final Set<String> ALLOWED = Set.of("PICKED_UP", "ON_THE_WAY", "DELIVERED");
    private final CurrentUserService currentUser;
    private final DriverProfileRepository drivers;
    private final DeliveryRepository deliveries;

    @GetMapping("/deliveries/current")
    @Transactional(readOnly = true)
    public ResponseEntity<SuccessResponse<List<DeliveryResponse>>> currentDeliveries() {
        AppUser driver = currentUser.getCurrentUser();
        requireApproved(driver);
        List<DeliveryResponse> result = deliveries.findAll().stream()
                .filter(d -> d.getDriver().getId().equals(driver.getId()))
                .filter(d -> !Set.of("DELIVERED", "CANCELLED", "FAILED").contains(d.getStatus()))
                .map(this::response).toList();
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, result));
    }

    @PatchMapping("/availability")
    @Transactional
    public ResponseEntity<SuccessResponse<Boolean>> availability(@Valid @RequestBody DriverAvailabilityRequest request) {
        DriverProfile profile = requireApproved(currentUser.getCurrentUser());
        if (request.getAvailable() && deliveries.findFirstByDriverAndStatusInOrderByAssignedAtDesc(
                profile.getUser(), List.of("ASSIGNED", "PICKED_UP", "ON_THE_WAY")).isPresent())
            throw new CustomException(HttpStatus.CONFLICT, "Complete your active delivery before becoming available");
        profile.setAvailable(request.getAvailable());
        drivers.save(profile);
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, profile.isAvailable()));
    }

    @PatchMapping("/deliveries/{id}/status")
    @Transactional
    public ResponseEntity<SuccessResponse<DeliveryResponse>> updateStatus(@PathVariable Long id, @Valid @RequestBody DeliveryStatusRequest request) {
        AppUser driver = currentUser.getCurrentUser();
        requireApproved(driver);
        Delivery delivery = deliveries.findById(id).orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, "Delivery not found"));
        if (!delivery.getDriver().getId().equals(driver.getId())) throw new CustomException(HttpStatus.FORBIDDEN, "This delivery is not assigned to you");
        String next = request.getStatus().trim().toUpperCase();
        if (!ALLOWED.contains(next)) throw new CustomException(HttpStatus.BAD_REQUEST, "Unsupported delivery status");
        String current = delivery.getStatus();
        boolean valid = (current.equals("ASSIGNED") && next.equals("PICKED_UP"))
                || (current.equals("PICKED_UP") && next.equals("ON_THE_WAY"))
                || (current.equals("ON_THE_WAY") && next.equals("DELIVERED"));
        if (!valid) throw new CustomException(HttpStatus.BAD_REQUEST, "Invalid delivery status transition");
        delivery.setStatus(next);
        if (next.equals("PICKED_UP")) delivery.setPickedUpAt(LocalDateTime.now());
        if (next.equals("DELIVERED")) {
            delivery.setDeliveredAt(LocalDateTime.now());
            DriverProfile profile = drivers.findByUser(driver).orElseThrow();
            profile.setAvailable(true);
            drivers.save(profile);
        }
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, response(delivery)));
    }

    @PostMapping("/location")
    @Transactional
    public ResponseEntity<SuccessResponse<DeliveryResponse>> updateLocation(@Valid @RequestBody DriverLocationRequest request) {
        AppUser driver = currentUser.getCurrentUser();
        requireApproved(driver);
        Delivery delivery = deliveries.findFirstByDriverAndStatusInOrderByAssignedAtDesc(
                driver, List.of("ASSIGNED", "PICKED_UP", "ON_THE_WAY"))
                .orElseThrow(() -> new CustomException(HttpStatus.CONFLICT, "You have no active delivery"));
        delivery.setDriverLatitude(request.getLatitude());
        delivery.setDriverLongitude(request.getLongitude());
        delivery.setDistanceKm(request.getDistanceKm());
        delivery.setEtaMinutes(request.getEtaMinutes());
        delivery.setLastLocationAt(LocalDateTime.now());
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, response(delivery)));
    }

    private DriverProfile requireApproved(AppUser user) {
        DriverProfile profile = drivers.findByUser(user).orElseThrow(() -> new CustomException(HttpStatus.FORBIDDEN, "Driver profile not found"));
        if (!profile.isApproved() || !user.isEnabled()) throw new CustomException(HttpStatus.FORBIDDEN, "Driver account is awaiting admin approval");
        return profile;
    }

    private DeliveryResponse response(Delivery d) {
        DeliveryResponse r = new DeliveryResponse();
        r.setId(d.getId());
        r.setOrderId(d.getOrder().getOrderId());
        r.setDriverUserId(d.getDriver().getId());
        r.setDriverName(d.getDriver().getEmail());
        r.setStatus(d.getStatus());
        r.setDeliveryAddress(d.getOrder().getDeliveryAddress());
        r.setDestinationLatitude(d.getOrder().getDeliveryLatitude());
        r.setDestinationLongitude(d.getOrder().getDeliveryLongitude());
        r.setDriverLatitude(d.getDriverLatitude());
        r.setDriverLongitude(d.getDriverLongitude());
        r.setDistanceKm(d.getDistanceKm());
        r.setEtaMinutes(d.getEtaMinutes());
        r.setAssignedAt(d.getAssignedAt());
        r.setPickedUpAt(d.getPickedUpAt());
        r.setDeliveredAt(d.getDeliveredAt());
        r.setLastLocationAt(d.getLastLocationAt());
        return r;
    }
}
