package com.premisave.listing.service;

import com.premisave.listing.entity.Payment;
import com.premisave.listing.enums.PaymentMethod;
import com.premisave.listing.enums.PaymentStatus;
import com.premisave.listing.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * AdPromotionService is injected lazily to break the circular dependency:
     * PaymentService → AdPromotionService → PaymentService
     */
    @Lazy
    @Autowired
    private AdPromotionService adPromotionService;

    // ====================== PROCESS PAYMENT ======================

    /**
     * Initiates a payment record.
     *
     * - MPESA: sets status PENDING; actual confirmation comes via callback.
     * - Other methods (CARD, PAYPAL): sets COMPLETED immediately.
     *   NOTE: In production, non-M-Pesa methods must call their respective
     *   gateway SDK here and only set COMPLETED on a successful gateway response.
     *
     * Receipts are NOT generated server-side. The frontend constructs receipts
     * from the Payment response data (id, amount, status, method, paidAt, transactionRef).
     *
     * @param userId         the paying user
     * @param subscriptionId optional — null when paying for a promotion
     * @param amount         amount to charge
     * @param method         payment method chosen by the user
     * @return the persisted Payment entity
     */
    @Transactional
    public Payment processPayment(String userId,
                                  String subscriptionId,
                                  BigDecimal amount,
                                  PaymentMethod method) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be null or blank.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive.");
        }
        if (method == null) {
            throw new IllegalArgumentException("Payment method must be specified.");
        }

        Payment payment = new Payment();
        payment.setUserId(userId);
        payment.setSubscriptionId(subscriptionId);
        payment.setAmount(amount);
        payment.setMethod(method);
        payment.setTransactionRef("TXN-" + System.currentTimeMillis());

        if (method == PaymentMethod.MPESA) {
            // M-Pesa is async — stay PENDING until callback arrives
            payment.setStatus(PaymentStatus.PENDING);
            payment.setPaidAt(null);
        } else {
            // Synchronous methods — mark completed immediately
            // TODO: Replace with actual gateway integration (Stripe, PayPal SDK, etc.)
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setPaidAt(LocalDateTime.now());
        }

        Payment saved = paymentRepository.save(payment);

        log.info("Payment initiated: id={}, method={}, amount={}, status={}, user={}",
                saved.getId(), method, amount, saved.getStatus(), userId);
        return saved;
    }

    // ====================== M-PESA CALLBACK ======================

    /**
     * Handles the STK Push callback from Safaricom Daraja.
     *
     * The callback structure from Safaricom is:
     * {
     *   "Body": {
     *     "stkCallback": {
     *       "MerchantRequestID": "...",
     *       "CheckoutRequestID": "...",   ← this is what we store as transactionRef
     *       "ResultCode": 0,              ← 0 = success
     *       "ResultDesc": "The service request is processed successfully."
     *     }
     *   }
     * }
     *
     * We match on CheckoutRequestID which must have been stored in transactionRef
     * when the STK push was initiated.
     */
    @Transactional
    public void handleMpesaCallback(Map<String, Object> callbackPayload) {
        try {
            if (callbackPayload == null) {
                log.warn("M-Pesa callback received with null payload — ignoring.");
                return;
            }

            Object bodyObj = callbackPayload.get("Body");
            if (!(bodyObj instanceof Map)) {
                log.warn("M-Pesa callback: 'Body' is missing or not a map — payload: {}", callbackPayload);
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) bodyObj;

            Object stkObj = body.get("stkCallback");
            if (!(stkObj instanceof Map)) {
                log.warn("M-Pesa callback: 'stkCallback' is missing or not a map.");
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> stkCallback = (Map<String, Object>) stkObj;

            Object resultCodeObj = stkCallback.get("ResultCode");
            String checkoutRequestId = (String) stkCallback.get("CheckoutRequestID");
            String resultDesc       = (String) stkCallback.get("ResultDesc");

            if (checkoutRequestId == null || checkoutRequestId.isBlank()) {
                log.warn("M-Pesa callback: CheckoutRequestID is missing — cannot match payment.");
                return;
            }

            // ResultCode 0 = success; anything else = failure
            boolean isSuccess = "0".equals(String.valueOf(resultCodeObj));
            PaymentStatus status = isSuccess ? PaymentStatus.COMPLETED : PaymentStatus.FAILED;

            List<Payment> payments = paymentRepository.findByTransactionRef(checkoutRequestId);
            if (payments.isEmpty()) {
                log.warn("M-Pesa callback: no payment found for CheckoutRequestID={}", checkoutRequestId);
                return;
            }

            Payment payment = payments.get(0);

            // Idempotency guard — don't reprocess an already-confirmed payment
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                log.info("M-Pesa callback: payment {} already COMPLETED — skipping.", payment.getId());
                return;
            }

            payment.setStatus(status);
            payment.setPaidAt(LocalDateTime.now());
            paymentRepository.save(payment);

            if (isSuccess) {
                log.info("M-Pesa payment CONFIRMED: paymentId={}, ref={}", payment.getId(), checkoutRequestId);

                // Trigger downstream activation (promotion or subscription)
                notifyPaymentConfirmed(payment);
            } else {
                log.warn("M-Pesa payment FAILED: ref={}, reason={}", checkoutRequestId, resultDesc);
            }

        } catch (Exception e) {
            log.error("Unexpected error processing M-Pesa callback: {}", e.getMessage(), e);
            // Do NOT rethrow — Safaricom expects a 200 OK even on our internal errors
        }
    }

    /**
     * After M-Pesa confirms a payment, activate whatever service the payment was for.
     * Currently handles promotion activation; extend here for subscriptions.
     */
    private void notifyPaymentConfirmed(Payment payment) {
        try {
            adPromotionService.activatePromotionAfterMpesaPayment(payment.getId());
        } catch (Exception e) {
            log.error("Failed to activate promotion after payment {}: {}", payment.getId(), e.getMessage(), e);
        }

        // If subscriptionId is set, the subscription was also waiting on this payment
        if (payment.getSubscriptionId() != null) {
            log.info("Payment {} linked to subscriptionId {} confirmed — subscription is now active.",
                    payment.getId(), payment.getSubscriptionId());
            // SubscriptionService doesn't need a callback because it's created synchronously
            // for non-M-Pesa. For M-Pesa subscriptions, add activation logic here.
        }
    }

    // ====================== QUERIES ======================

    public List<Payment> getUserPayments(String userId) {
        return paymentRepository.findByUserId(userId);
    }

    public Payment getPaymentById(String id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + id));
    }
}