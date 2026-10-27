package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.model.response.OrderResponse;
import com.api.v1.bearerauth.service.impl.AbaPayWayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments/aba-payway")
@RequiredArgsConstructor
public class AbaPayWayController {
    private final AbaPayWayService abaPayWayService;

    @PostMapping("/orders/{orderId}/checkout")
    public ResponseEntity<Map<String, String>> createCheckout(@PathVariable Long orderId) {
        return ResponseEntity.ok(abaPayWayService.createCheckout(orderId));
    }

    @PostMapping("/orders/{orderId}/verify")
    public ResponseEntity<OrderResponse> verify(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> request) {
        String transactionId = request == null ? null : request.get("transactionId");
        if (transactionId == null || transactionId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(abaPayWayService.verify(orderId, transactionId));
    }
}
