package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.common.response.SuccessResponse;
import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.model.entity.*;
import com.api.v1.bearerauth.model.request.DeliveryAddressRequest;
import com.api.v1.bearerauth.model.response.DeliveryAddressResponse;
import com.api.v1.bearerauth.repository.CustomerRepository;
import com.api.v1.bearerauth.security.CurrentUserService;
import com.api.v1.bearerauth.util.ApiResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user/delivery-address")
@RequiredArgsConstructor
public class DeliveryAddressController {
    private final CustomerRepository repo;
    private final CurrentUserService current;

    @GetMapping
    public ResponseEntity<SuccessResponse<DeliveryAddressResponse>> get() {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, response()));
    }

    @PutMapping
    public ResponseEntity<SuccessResponse<DeliveryAddressResponse>> save(@Valid @RequestBody DeliveryAddressRequest r) {
        Customer c = repo.findByUser(current.getCurrentUser()).orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, "Customer profile not found"));
        c.setAddress(r.getAddress().trim());
        c.setCity(r.getCity().trim());
        c.setPhoneNumber(r.getPhone().trim());
        c.setDeliveryLatitude(r.getLatitude());
        c.setDeliveryLongitude(r.getLongitude());
        repo.save(c);
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, response(c)));
    }

    private DeliveryAddressResponse response() {
        Customer c = repo.findByUser(current.getCurrentUser()).orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, "Customer profile not found"));
        return response(c);
    }

    private DeliveryAddressResponse response(Customer c) {
        return new DeliveryAddressResponse(c.getAddress(), c.getCity(), c.getPhoneNumber(), c.getDeliveryLatitude(), c.getDeliveryLongitude());
    }
}
