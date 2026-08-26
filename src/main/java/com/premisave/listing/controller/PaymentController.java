package com.premisave.listing.controller;

import com.premisave.listing.entity.Payment;
import com.premisave.listing.service.PaymentService;
import com.premisave.listing.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;


@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final JwtUtil jwtUtil;

    /**
     * Generic payment endpoint — debits the caller's wallet via
     * wallet-service for an arbitrary listing-service charge, tagged with a
     * `service` name for wallet-service's own reporting (e.g.
     * "AD_SUBSCRIPTION").
     */
    @PostMapping
    public ResponseEntity<Payment> processPayment(
            @RequestParam BigDecimal amount,
            @RequestParam String service,
            @RequestParam(required = false) String description,
            @RequestHeader("Authorization") String authorization) {

        String userId = jwtUtil.extractUserId(authorization);
        Payment payment = paymentService.processPayment(userId, null, amount, service,
                description != null ? description : "Payment via listing service");
        return ResponseEntity.ok(payment);
    }

    // ====================== QUERIES ======================

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