package com.api.v1.bearerauth.model.bakong;

import kh.gov.nbc.bakong_khqr.model.KHQRCurrency;

public record BakongQrRequest(
        Long orderId,
        Double amount,
        KHQRCurrency currency,
        String billNumber,
        Integer expirationMinutes,
        String merchantName,
        String merchantCity
) {
    public BakongQrRequest {
        currency = currency == null ? KHQRCurrency.KHR : currency;
        expirationMinutes = expirationMinutes == null ? 15 : Math.min(Math.max(expirationMinutes, 1), 30);
        billNumber = billNumber == null || billNumber.isBlank()
                ? "FOODIE-" + System.currentTimeMillis()
                : billNumber.trim();
        merchantName = merchantName == null || merchantName.isBlank() ? "Foodie" : merchantName.trim();
        merchantCity = merchantCity == null || merchantCity.isBlank() ? "PHNOM PENH" : merchantCity.trim();
    }
}
