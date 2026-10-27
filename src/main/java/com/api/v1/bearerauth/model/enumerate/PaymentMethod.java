package com.api.v1.bearerauth.model.enumerate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentMethod {
    CASH("Cash"),
    QR_PAYMENT("QR Payment"),
    CARD("Card"),
    ABA_PAYWAY("ABA PayWay");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static PaymentMethod fromValue(String value) {
        if (value == null) return null;
        String v = value.trim().toUpperCase().replace(' ', '_');
        return valueOf(v);
    }
}
