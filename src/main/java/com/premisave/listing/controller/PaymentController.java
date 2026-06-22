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

    // Traditional Payment (non-M-Pesa)
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

    /**
     * Initiates an M-Pesa STK Push for a standalone payment (not tied to a promotion).
     *
     * Flow:
     * 1. Create a PENDING Payment record first — this gives us a paymentId.
     * 2. Pass paymentId into initiateStkPush so it can store the CheckoutRequestID
     *    returned by Safaricom as the payment's transactionRef.
     * 3. When Safaricom fires the callback, PaymentService matches on transactionRef
     *    (CheckoutRequestID) and marks the payment COMPLETED.
     *
     * NOTE: For promotion payments, the STK push is initiated by AdPromotionService,
     * not this endpoint. This endpoint is for direct/manual payments only.
     */
    @PostMapping("/mpesa/stkpush")
    public ResponseEntity<Map<String, Object>> initiateMpesaStkPush(
            @RequestBody MpesaStkPushRequest request,
            @RequestHeader("Authorization") String authorization) {

        String userId = jwtUtil.extractUserId(authorization);

        // Step 1: Create a PENDING payment record so we have a paymentId to link
        Payment payment = paymentService.processPayment(
                userId,
                null,
                request.getAmount(),
                PaymentMethod.MPESA
        );

        // Step 2: Initiate STK push and link it to the payment record.
        // MpesaService will update payment.transactionRef = CheckoutRequestID
        // so the callback can find this payment later.
        Map<String, Object> response = mpesaService.initiateStkPush(request, payment.getId());

        log.info("M-Pesa STK Push initiated for user: {}, paymentId: {}", userId, payment.getId());
        return ResponseEntity.ok(response);
    }

    // ====================== M-PESA CALLBACKS (PUBLIC - NO AUTH) ======================
    // These are called by Safaricom — they must be permitted in SecurityConfig
    // and excluded from rate limiting in WebConfig.

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