package com.premisave.listing.controller;

import com.premisave.listing.entity.Payment;
import com.premisave.listing.enums.PaymentMethod;
import com.premisave.listing.service.PaymentService;
import com.premisave.listing.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<Payment> processPayment(
            @RequestParam String subscriptionId,
            @RequestParam BigDecimal amount,
            @RequestParam PaymentMethod method,
            @RequestHeader("Authorization") String authorization) {

        String userId = jwtUtil.extractUserId(authorization);
        
        Payment payment = paymentService.processPayment(userId, subscriptionId, amount, method);
        return ResponseEntity.ok(payment);
    }

    @GetMapping("/me")
    public ResponseEntity<List<Payment>> getMyPayments(
            @RequestHeader("Authorization") String authorization) {

        String userId = jwtUtil.extractUserId(authorization);
        List<Payment> payments = paymentService.getUserPayments(userId);
        return ResponseEntity.ok(payments);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payment> getPaymentById(@PathVariable String id) {
        Payment payment = paymentService.getPaymentById(id);
        return ResponseEntity.ok(payment);
    }
}