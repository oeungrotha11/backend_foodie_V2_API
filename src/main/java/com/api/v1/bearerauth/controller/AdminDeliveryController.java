package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.common.response.SuccessResponse;
import com.api.v1.bearerauth.model.entity.*;
import com.api.v1.bearerauth.model.enumerate.OrderType;
import com.api.v1.bearerauth.model.enumerate.Roles;
import com.api.v1.bearerauth.model.request.*;
import com.api.v1.bearerauth.model.response.*;
import com.api.v1.bearerauth.repository.*;
import com.api.v1.bearerauth.util.ApiResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/deliveries")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDeliveryController {
    private final AppUserRepository users;
    private final DriverProfileRepository drivers;
    private final DeliveryRepository deliveries;
    private final OrderTbRepository orders;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/drivers")
    @Transactional
    public ResponseEntity<SuccessResponse<DriverResponse>> createDriver(@Valid @RequestBody AdminCreateDriverRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (users.findByEmail(email).isPresent()) throw new CustomException(HttpStatus.CONFLICT, "Email already exists");
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPasswordConfigured(true);
        user.setRole(Roles.DRIVER);
        user.setEnabled(false);
        users.save(user);

        DriverProfile profile = new DriverProfile();
        profile.setUser(user);
        profile.setApproved(false);
        profile.setAvailable(false);
        profile.setFullName(request.getFullName().trim());
        profile.setPhoneNumber(request.getPhoneNumber());
        profile.setVehicleType(request.getVehicleType());
        profile.setVehiclePlate(request.getVehiclePlate());
        drivers.save(profile);

        DriverResponse response = driverResponse(profile, request.getFullName(), request.getPhoneNumber());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseUtil.success(HttpStatus.CREATED, response));
    }

    @GetMapping("/drivers")
    @Transactional(readOnly = true)
    public ResponseEntity<SuccessResponse<List<DriverResponse>>> listDrivers() {
        List<DriverResponse> result = drivers.findAll().stream()
                .map(p -> driverResponse(p, p.getFullName(), p.getPhoneNumber())).toList();
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, result));
    }

    @PatchMapping("/drivers/{userId}/approval")
    @Transactional
    public ResponseEntity<SuccessResponse<DriverResponse>> approveDriver(@PathVariable Long userId, @RequestParam boolean approved) {
        AppUser user = users.findById(userId).orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, "Driver account not found"));
        if (user.getRole() != Roles.DRIVER) throw new CustomException(HttpStatus.BAD_REQUEST, "Account is not a driver");
        DriverProfile profile = drivers.findByUser(user).orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, "Driver profile not found"));
        profile.setApproved(approved);
        profile.setAvailable(approved && profile.isAvailable());
        user.setEnabled(approved);
        users.save(user);
        drivers.save(profile);
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, driverResponse(profile, user.getEmail(), null)));
    }

    @PostMapping("/assign")
    @Transactional
    public ResponseEntity<SuccessResponse<DeliveryResponse>> assign(@Valid @RequestBody DeliveryAssignmentRequest request) {
        OrderTb order = orders.findById(request.getOrderId()).orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, "Order not found"));
        if (order.getOrderType() != OrderType.DELIVERY) throw new CustomException(HttpStatus.BAD_REQUEST, "Only delivery orders can be assigned");
        if (order.getDeliveryLatitude() == null || order.getDeliveryLongitude() == null)
            throw new CustomException(HttpStatus.BAD_REQUEST, "This order has no saved delivery coordinates");
        if (deliveries.findByOrderOrderId(order.getOrderId()).isPresent())
            throw new CustomException(HttpStatus.CONFLICT, "This order already has a delivery assignment");

        AppUser driverUser = users.findById(request.getDriverUserId()).orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, "Driver not found"));
        DriverProfile profile = drivers.findByUser(driverUser).orElseThrow(() -> new CustomException(HttpStatus.BAD_REQUEST, "Account is not a registered driver"));
        if (!profile.isApproved() || !driverUser.isEnabled()) throw new CustomException(HttpStatus.FORBIDDEN, "Driver is not approved");
        if (!profile.isAvailable()) throw new CustomException(HttpStatus.CONFLICT, "Driver is not available");

        Delivery delivery = new Delivery();
        delivery.setOrder(order);
        delivery.setDriver(driverUser);
        delivery.setStatus("ASSIGNED");
        profile.setAvailable(false);
        deliveries.save(delivery);
        drivers.save(profile);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseUtil.success(HttpStatus.CREATED, response(delivery)));
    }

    private DriverResponse driverResponse(DriverProfile p, String fullName, String phone) {
        DriverResponse r = new DriverResponse();
        r.setUserId(p.getUser().getId());
        r.setEmail(p.getUser().getEmail());
        r.setFullName(fullName);
        r.setPhoneNumber(phone);
        r.setVehicleType(p.getVehicleType());
        r.setVehiclePlate(p.getVehiclePlate());
        r.setApproved(p.isApproved());
        r.setAvailable(p.isAvailable());
        return r;
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
