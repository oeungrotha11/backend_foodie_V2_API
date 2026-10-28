package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.model.bakong.BakongQrRequest;
import com.api.v1.bearerauth.model.bakong.BakongQrResult;
import com.api.v1.bearerauth.service.impl.BakongOpenApiService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/bakong")
@RequiredArgsConstructor
public class BakongController {
    private final BakongOpenApiService bakongOpenApiService;

    @PostMapping("/verify-token")
    public ResponseEntity<Map<String, Object>> verifyToken() {
        Map<String, Object> result = bakongOpenApiService.verifyToken();

        Object code = result.get("responseCode");
        boolean valid = code instanceof Number
                && ((Number) code).intValue() == 0;

        Map<String, Object> response = new HashMap<>();
        response.put("valid", valid);
        response.put("message", valid
                ? "Bakong API request succeeded and account exists."
                : "Bakong API request failed or account could not be verified.");
        response.put("details", result);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/generate-qr")
    public ResponseEntity<BakongQrResult> generateQr(@Valid @RequestBody BakongQrRequest request) {
        return ResponseEntity.ok(bakongOpenApiService.generateQr(request));
    }

    @PostMapping("/check-transaction")
    public Map<?, ?> checkTransaction(@RequestBody Map<String, String> request) {
        String md5 = request.get("md5");
        return bakongOpenApiService.checkTransactionByMd5(md5);
    }
}
