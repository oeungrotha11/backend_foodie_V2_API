package com.api.v1.bearerauth.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BakongOpenApiService {
    private final RestClient client;
    private final String apiToken;

    public BakongOpenApiService(
            RestClient.Builder builder,
            @Value("${bakong.base-url}") String baseUrl,
            @Value("${bakong.api-token:}") String apiToken) {
        this.client = builder.baseUrl(baseUrl).build();
        this.apiToken = apiToken == null ? "" : apiToken.trim();
    }

    /**
     * Checks the server-side Bakong token against the official Open API.
     * The token is never returned to the Flutter client.
     */
    public boolean verifyToken() {
        if (apiToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Bakong API token is not configured on the backend.");
        }

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
}
