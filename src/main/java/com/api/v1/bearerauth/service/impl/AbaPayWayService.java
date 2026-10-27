package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.entity.OrderTb;
import com.api.v1.bearerauth.model.enumerate.OrderStatus;
import com.api.v1.bearerauth.model.enumerate.PaymentMethod;
import com.api.v1.bearerauth.model.request.OrderStatusRequest;
import com.api.v1.bearerauth.model.response.OrderResponse;
import com.api.v1.bearerauth.repository.OrderTbRepository;
import com.api.v1.bearerauth.security.CurrentUserService;
import com.api.v1.bearerauth.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AbaPayWayService {
    private final OrderTbRepository orderRepository;
    private final CurrentUserService currentUserService;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @Value("${payway.base-url}")
    private String baseUrl;
    @Value("${payway.merchant-id}")
    private String merchantId;
    @Value("${payway.api-key}")
    private String apiKey;
    @Value("${payway.currency:USD}")
    private String currency;
    @Value("${payway.payment-option:abapay_khqr}")
    private String paymentOption;

    private final RestClient restClient = RestClient.create();
    private static final DateTimeFormatter REQ_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Transactional
    public Map<String, String> createCheckout(Long orderId) {
        ensureConfigured();
        OrderTb order = findOwnedOrder(orderId);
        if (order.getStatus() != OrderStatus.processing) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "Only processing orders can be paid");
        }
        if (order.getPaymentMethod() != PaymentMethod.ABA_PAYWAY) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "This order is not using ABA PayWay");
        }
        String reqTime = LocalDateTime.now().format(REQ_TIME);
        String tranId = String.valueOf(System.currentTimeMillis());
        String amount = BigDecimal.valueOf(order.getTotalAmount()).setScale(2).toPlainString();
        String firstName = order.getCustomer().getCustomerName() == null ? "Foodie" : order.getCustomer().getCustomerName();
        String lastName = "Customer";
        String email = order.getCustomer().getUser().getEmail();
        String phone = order.getCustomer().getPhoneNumber() == null ? "" : order.getCustomer().getPhoneNumber();

        // Keep the transaction ID on the order so verification can only confirm this order's latest attempt.
        order.setPaymentReference(tranId);
        orderRepository.save(order);

        String items = Base64.getEncoder().encodeToString("[]".getBytes(StandardCharsets.UTF_8));
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("req_time", reqTime);
        fields.put("merchant_id", merchantId);
        fields.put("tran_id", tranId);
        fields.put("amount", amount);
        fields.put("items", items);
        fields.put("shipping", String.valueOf(order.getDeliveryFee() == null ? 0.0 : order.getDeliveryFee()));
        fields.put("firstname", firstName);
        fields.put("lastname", lastName);
        fields.put("email", email);
        fields.put("phone", phone);
        fields.put("type", "purchase");
        fields.put("payment_option", paymentOption);
        fields.put("currency", currency);
        fields.put("hash", hmacSha512(apiKey, concat(fields)));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        fields.forEach((key, value) -> { if (!key.equals("hash")) form.add(key, value); });
        form.add("hash", fields.get("hash"));

        String html = restClient.post()
                .uri(baseUrl + "/api/payment-gateway/v1/payments/purchase")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(String.class);
        if (html == null || !html.toLowerCase().contains("<html")) {
            throw new CustomException(HttpStatus.BAD_GATEWAY, "ABA PayWay did not return a checkout page");
        }
        return Map.of("transactionId", tranId, "checkoutHtml", html);
    }

    @Transactional
    public OrderResponse verify(Long orderId, String transactionId) {
        ensureConfigured();
        OrderTb order = findOwnedOrder(orderId);
        if (order.getPaymentMethod() != PaymentMethod.ABA_PAYWAY
                || order.getPaymentReference() == null
                || !order.getPaymentReference().equals(transactionId)) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "Payment reference does not match this order");
        }
        if (order.getStatus() == OrderStatus.purchased) {
            return orderService.get(orderId);
        }
        if (order.getStatus() != OrderStatus.processing) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "This order cannot be paid");
        }

        String reqTime = LocalDateTime.now().format(REQ_TIME);
        String signature = hmacSha512(apiKey, reqTime + merchantId + transactionId);
        Map<String, String> body = Map.of(
                "req_time", reqTime,
                "merchant_id", merchantId,
                "tran_id", transactionId,
                "hash", signature
        );
        Map<?, ?> response = restClient.post()
                .uri(baseUrl + "/api/payment-gateway/v1/payments/check-transaction-2")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (response == null || !(response.get("data") instanceof Map<?, ?> data)) {
            throw new CustomException(HttpStatus.BAD_GATEWAY, "Could not verify ABA PayWay payment");
        }
        String status = String.valueOf(data.get("payment_status"));
        String statusCode = String.valueOf(data.get("payment_status_code"));
        double paidAmount = parseAmount(data.get("total_amount"));
        String paidCurrency = String.valueOf(data.get("payment_currency"));
        if (!"APPROVED".equalsIgnoreCase(status) || !"0".equals(statusCode)
                || Math.abs(paidAmount - order.getTotalAmount()) > 0.009
                || !currency.equalsIgnoreCase(paidCurrency)) {
            throw new CustomException(HttpStatus.CONFLICT, "ABA PayWay has not confirmed the expected payment yet");
        }
        OrderStatusRequest statusRequest = new OrderStatusRequest();
        statusRequest.setStatus(OrderStatus.purchased);
        return orderService.updateStatus(orderId, statusRequest);
    }

    private OrderTb findOwnedOrder(Long id) {
        OrderTb order = orderRepository.findById(id)
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, "Order not found"));
        AppUser user = currentUserService.getCurrentUser();
        if (!"ADMIN".equals(user.getRole().name())
                && !order.getCustomer().getUser().getId().equals(user.getId())) {
            throw new CustomException(HttpStatus.FORBIDDEN, "You can only access your own order");
        }
        return order;
    }

    private void ensureConfigured() {
        if (merchantId == null || merchantId.isBlank() || apiKey == null || apiKey.isBlank()) {
            throw new CustomException(HttpStatus.SERVICE_UNAVAILABLE,
                    "ABA PayWay is not configured. Set PAYWAY_MERCHANT_ID and PAYWAY_API_KEY.");
        }
    }

    private String concat(Map<String, String> fields) {
        // PayWay purchase signature uses the fields in the documented canonical order.
        String[] order = {"req_time", "merchant_id", "tran_id", "amount", "items", "shipping",
                "firstname", "lastname", "email", "phone", "type", "payment_option",
                "currency"};
        StringBuilder value = new StringBuilder();
        for (String key : order) value.append(fields.getOrDefault(key, ""));
        return value.toString();
    }

    private String hmacSha512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign ABA PayWay request", ex);
        }
    }

    private double parseAmount(Object value) {
        try { return Double.parseDouble(String.valueOf(value)); }
        catch (Exception ex) { return -1; }
    }
}
