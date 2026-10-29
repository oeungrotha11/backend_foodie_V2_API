package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.model.bakong.BakongQrRequest;
import com.api.v1.bearerauth.model.bakong.BakongQrResult;
import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.entity.BakongPayment;
import com.api.v1.bearerauth.model.entity.OrderTb;
import com.api.v1.bearerauth.model.enumerate.OrderStatus;
import com.api.v1.bearerauth.model.enumerate.PaymentStatus;
import com.api.v1.bearerauth.repository.BakongPaymentRepository;
import com.api.v1.bearerauth.repository.OrderTbRepository;
import com.api.v1.bearerauth.security.CurrentUserService;
import com.api.v1.bearerauth.service.impl.BakongOpenApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/bakong")
@RequiredArgsConstructor
public class BakongController {
    private final BakongOpenApiService bakongOpenApiService;
    private final BakongPaymentRepository paymentRepository;
    private final OrderTbRepository orderRepository;
    private final CurrentUserService currentUserService;

    @PostMapping("/verify-token")
    public ResponseEntity<Map<String, Object>> verifyToken() {
        Map<String, Object> result = bakongOpenApiService.verifyToken();
        Object code = result.get("responseCode");
        boolean valid = code instanceof Number && ((Number) code).intValue() == 0;
        Map<String, Object> response = new HashMap<>();
        response.put("valid", valid);
        response.put("message", valid ? "Bakong API request succeeded and account exists."
                : "Bakong API request failed or account could not be verified.");
        response.put("details", result);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/generate-qr")
    @Transactional
    public ResponseEntity<BakongQrResult> generateQr(@RequestBody BakongQrRequest request) {
        if (request.orderId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId is required to bind the QR to an order.");
        }
        OrderTb order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));
        ensureOrderOwner(order);

        if (order.getStatus() != OrderStatus.processing) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only processing orders can start payment.");
        }
        if (request.amount() == null || Math.abs(request.amount() - order.getTotalAmount()) > 0.01) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment amount must match the order total.");
        }

        BakongPayment existing = paymentRepository.findByOrderId(order.getOrderId()).orElse(null);
        if (existing != null && "PAID".equals(existing.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This order has already been paid.");
        }
        if (existing != null && "PENDING".equals(existing.getStatus())
                && existing.getExpiresAt().isAfter(LocalDateTime.now())) {
            long minutesLeft = Math.max(1, java.time.Duration.between(LocalDateTime.now(), existing.getExpiresAt()).toMinutes());
            return ResponseEntity.ok(new BakongQrResult(existing.getQrPayload(), existing.getMd5(),
                    existing.getAmount(), existing.getCurrency(), "FOODIE-" + existing.getOrderId(), (int) minutesLeft));
        }

        BakongQrResult qr = bakongOpenApiService.generateQr(request);
        BakongPayment payment = existing == null ? new BakongPayment() : existing;
        payment.setOrderId(order.getOrderId());
        payment.setMd5(qr.md5());
        payment.setQrPayload(qr.qr());
        payment.setAmount(qr.amount());
        payment.setCurrency(qr.currency());
        payment.setStatus("PENDING");
        payment.setPaidAt(null);
        payment.setExpiresAt(LocalDateTime.now().plusMinutes(qr.expirationMinutes()));
        paymentRepository.saveAndFlush(payment);
        order.setPaymentStatus(PaymentStatus.PENDING);
        orderRepository.save(order);
        return ResponseEntity.ok(qr);
    }

    @PostMapping("/check-transaction")
    @Transactional
    public Map<?, ?> checkTransaction(@RequestBody Map<String, String> request) {
        String md5 = request.get("md5");
        if (md5 == null || !md5.matches("(?i)^[a-f0-9]{32}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid KHQR MD5 hash is required.");
        }
        BakongPayment payment = paymentRepository.findByMd5IgnoreCase(md5)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No payment request is registered for this QR."));
        OrderTb order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));
        ensureOrderOwner(order);

        if ("PAID".equals(payment.getStatus())) {
            return Map.of("responseCode", 0, "responseMessage", "Payment already confirmed.",
                    "data", Map.of("md5", payment.getMd5(), "status", "SUCCESS", "alreadyPaid", true));
        }
        if (!payment.getExpiresAt().isAfter(LocalDateTime.now())) {
            payment.setStatus("EXPIRED");
            paymentRepository.save(payment);
            order.setPaymentStatus(PaymentStatus.EXPIRED);
            orderRepository.save(order);
            return Map.of("responseCode", 1, "responseMessage", "This QR payment request has expired.");
        }

        Map<?, ?> result = bakongOpenApiService.checkTransactionByMd5(payment.getMd5());
        Object dataValue = result.get("data");
        boolean simulated = dataValue instanceof Map<?, ?> data && Boolean.TRUE.equals(data.get("testMode"));
        Object code = result.get("responseCode");
        boolean confirmed = code instanceof Number && ((Number) code).intValue() == 0 && !simulated;
        if (confirmed) {
            // Idempotent: a paid payment stays paid, and the order is transitioned once.
            payment.setStatus("PAID");
            payment.setPaidAt(LocalDateTime.now());
            paymentRepository.save(payment);
            // Payment confirmation must never change the chef's order status.
            order.setPaymentStatus(PaymentStatus.PAID);
            orderRepository.save(order);
        }
        return result;
    }

    private void ensureOrderOwner(OrderTb order) {
        AppUser user = currentUserService.getCurrentUser();
        boolean admin = user.getRole() != null && user.getRole().name().equals("ADMIN");
        if (!admin && (order.getCustomer() == null || order.getCustomer().getUser() == null
                || !order.getCustomer().getUser().getId().equals(user.getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this order.");
        }
    }
}
