package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.model.bakong.BakongQrRequest;
import com.api.v1.bearerauth.model.bakong.BakongQrResult;
import com.api.v1.bearerauth.service.impl.BakongOpenApiService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/bakong")
@RequiredArgsConstructor
public class BakongController {
    private final BakongOpenApiService bakongOpenApiService;

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

    @PostMapping("/generate-qr")
    public ResponseEntity<BakongQrResult> generateQr(@Valid @RequestBody BakongQrRequest request) {
        return ResponseEntity.ok(bakongOpenApiService.generateQr(request));
    }

    @PostMapping("/check-transaction")
    public ResponseEntity<JsonNode> checkTransaction(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(bakongOpenApiService.checkTransactionByMd5(request.get("md5")));
    }
}
