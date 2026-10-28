package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.service.impl.BakongOpenApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/bakong")
@RequiredArgsConstructor
public class BakongController {
    private final BakongOpenApiService bakongOpenApiService;

    /**
     * Diagnostic endpoint for the signed-in app/admin to confirm backend
     * Bakong credentials are configured. Never returns the token itself.
     */
    @PostMapping("/verify-token")
    public ResponseEntity<Map<String, Object>> verifyToken() {
        boolean valid = bakongOpenApiService.verifyToken();
        return ResponseEntity.ok(Map.of(
                "valid", valid,
                "message", valid
                        ? "Bakong API token is valid."
                        : "Bakong API token was rejected. Check the token and environment."
        ));
    }
}
