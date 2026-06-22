package com.premisave.listing.controller;

import com.premisave.listing.dto.MpesaStkPushRequest;
import com.premisave.listing.entity.Payment;
import com.premisave.listing.enums.PaymentMethod;
import com.premisave.listing.service.MpesaService;
import com.premisave.listing.service.PaymentService;
import com.premisave.listing.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final MpesaService mpesaService;
    private final JwtUtil jwtUtil;

    // Traditional Payment
    @PostMapping
    public ResponseEntity<Payment> processPayment(
            @RequestParam BigDecimal amount,
            @RequestParam PaymentMethod method,
            @RequestHeader("Authorization") String authorization) {

        String userId = jwtUtil.extractUserId(authorization);
        Payment payment = paymentService.processPayment(userId, null, amount, method);
        return ResponseEntity.ok(payment);
    }

    // ====================== M-PESA STK PUSH ======================
    @PostMapping("/mpesa/stkpush")
    public ResponseEntity<Map<String, Object>> initiateMpesaStkPush(
            @RequestBody MpesaStkPushRequest request,
            @RequestHeader("Authorization") String authorization) {

        String userId = jwtUtil.extractUserId(authorization);
        Map<String, Object> response = mpesaService.initiateStkPush(request);

        log.info("M-Pesa STK Push initiated for user: {}", userId);
        return ResponseEntity.ok(response);
    }

    // ====================== M-PESA CALLBACKS (PUBLIC - NO AUTH) ======================
    @PostMapping("/mpesa/callback")
    public ResponseEntity<String> mpesaStkCallback(@RequestBody Map<String, Object> callbackData) {
        log.info("M-Pesa STK Callback Received");
        paymentService.handleMpesaCallback(callbackData);
        return ResponseEntity.ok("Success");
    }

    @PostMapping("/mpesa/confirmation")
    public ResponseEntity<String> mpesaConfirmation(@RequestBody Map<String, Object> payload) {
        log.info("M-Pesa C2B Confirmation Received: {}", payload);
        return ResponseEntity.ok("Confirmation received");
    }

    @PostMapping("/mpesa/validation")
    public ResponseEntity<String> mpesaValidation(@RequestBody Map<String, Object> payload) {
        log.info("M-Pesa C2B Validation Received: {}", payload);
        return ResponseEntity.ok("Validation successful");
    }

    @GetMapping("/me")
    public ResponseEntity<List<Payment>> getMyPayments(@RequestHeader("Authorization") String authorization) {
        String userId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(paymentService.getUserPayments(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payment> getPaymentById(@PathVariable String id) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }
}