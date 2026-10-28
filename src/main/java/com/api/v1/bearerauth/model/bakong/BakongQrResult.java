package com.api.v1.bearerauth.model.bakong;

public record BakongQrResult(
        String qr,
        String md5,
        Double amount,
        String currency,
        String billNumber,
        Integer expirationMinutes,
        Double displayAmountUsd
) {}
