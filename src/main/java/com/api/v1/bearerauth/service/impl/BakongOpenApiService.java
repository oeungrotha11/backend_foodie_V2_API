package com.api.v1.bearerauth.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.api.v1.bearerauth.model.bakong.BakongQrRequest;
import com.api.v1.bearerauth.model.bakong.BakongQrResult;
import kh.gov.nbc.bakong_khqr.BakongKHQR;
import kh.gov.nbc.bakong_khqr.model.KHQRData;
import kh.gov.nbc.bakong_khqr.model.KHQRResponse;
import kh.gov.nbc.bakong_khqr.model.MerchantInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class BakongOpenApiService {
    private final RestClient client;
    private final String apiToken;
    private final String accountId;

    public BakongOpenApiService(
            @Value("${bakong.base-url}") String baseUrl,
            @Value("${bakong.api-token:}") String apiToken,
            @Value("${bakong.account-id:}") String accountId) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
        this.apiToken = apiToken == null ? "" : apiToken.trim();
        this.accountId = accountId == null ? "" : accountId.trim();
    }

    public boolean verifyToken() {
        requireToken();
        JsonNode response = client.post()
                .uri("/v1/verify")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.has("responseCode")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Bakong returned an unexpected token verification response.");
        }
        return response.path("responseCode").asInt(-1) == 0;
    }

    /**
     * Creates a KHQR payload. This endpoint is not yet linked to order state;
     * the checkout flow must bind the QR hash to a persisted order before use.
     */
    public BakongQrResult generateQr(BakongQrRequest request) {
        requireToken();
        if (accountId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Bakong receiving account ID is not configured on the backend.");
        }
        if (request.amount() == null || request.amount() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero.");
        }

        MerchantInfo info = new MerchantInfo();
        info.setBakongAccountId(accountId);
        info.setMerchantName(request.merchantName());
        info.setMerchantCity(request.merchantCity());
        info.setCurrency(request.currency());
        info.setAmount(request.amount());
        info.setBillNumber(request.billNumber());
        info.setExpirationTimestamp(System.currentTimeMillis() + request.expirationMinutes() * 60_000L);

        KHQRResponse<KHQRData> generated = BakongKHQR.generateMerchant(info);
        if (generated == null || generated.getKHQRStatus() == null
                || generated.getKHQRStatus().getCode() != 0
                || generated.getData() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Bakong KHQR generation failed.");
        }

        KHQRData data = generated.getData();
        return new BakongQrResult(data.getQr(), data.getMd5(), request.amount(),
                request.currency().name(), request.billNumber(), request.expirationMinutes());
    }

    public JsonNode checkTransactionByMd5(String md5) {
        requireToken();
        if (md5 == null || !md5.matches("(?i)^[a-f0-9]{32}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid KHQR MD5 hash is required.");
        }
        return client.post()
                .uri("/v1/check_transaction_by_md5")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .body(Map.of("md5", md5))
                .retrieve()
                .body(JsonNode.class);
    }

    private void requireToken() {
        if (apiToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Bakong API token is not configured on the backend.");
        }
    }
}
