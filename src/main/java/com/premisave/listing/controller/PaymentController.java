package com.premisave.listing.controller;

import com.premisave.listing.entity.Payment;
import com.premisave.listing.enums.PaymentMethod;
import com.premisave.listing.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<Payment> processPayment(
            @RequestParam String subscriptionId,
            @RequestParam BigDecimal amount,
            @RequestParam PaymentMethod method,
            @RequestHeader("Authorization") String authorization) {
        
        String userId = "current-user-id"; // Extract from JWT in production
        Payment payment = paymentService.processPayment(userId, subscriptionId, amount, method);
        return ResponseEntity.ok(payment);
    }
}