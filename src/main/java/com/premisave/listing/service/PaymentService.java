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
    private final CurrencyService currencyService;

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
     * Currency handling:
     * - amountKes is the canonical amount in KES (always stored on the Payment record)
     * - currency is the user's preferred currency (defaults to KES if null/blank)
     * - If currency != KES, the amount is converted using the live Frankfurter rate
     * - The exchange rate used is stored on the Payment for audit/reconciliation
     * - M-Pesa always receives KES — conversion is transparent
     * - Stripe/PayPal/Airtel receive the target currency natively
     *
     * @param userId         the paying user
     * @param referenceId    optional reference ID (e.g. promotion ID); null is allowed
     * @param amountKes      amount in KES (the canonical backend currency)
     * @param method         payment method
     * @param currency       user's preferred display/charge currency (ISO 4217, defaults to KES)
     * @return the persisted Payment entity
     */
    @Transactional
    public Payment processPayment(String userId,
                                  String referenceId,
                                  BigDecimal amountKes,
                                  PaymentMethod method,
                                  String currency) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be null or blank.");
        }
        if (amountKes == null || amountKes.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive.");
        }
        if (method == null) {
            throw new IllegalArgumentException("Payment method must be specified.");
        }

        // Normalise currency — default to KES
        String targetCurrency = (currency == null || currency.isBlank())
                ? CurrencyService.BASE_CURRENCY
                : currency.toUpperCase();

        // Convert KES → target currency for the charge amount
        BigDecimal chargeAmount;
        BigDecimal exchangeRate;

        if (targetCurrency.equals(CurrencyService.BASE_CURRENCY)) {
            chargeAmount = amountKes;
            exchangeRate = BigDecimal.ONE;
        } else {
            exchangeRate = currencyService.getRate(targetCurrency);
            chargeAmount = currencyService.convertFromKes(amountKes, targetCurrency);
            log.info("Currency conversion: {} KES → {} {} (rate: {})",
                    amountKes, chargeAmount, targetCurrency, exchangeRate);
        }

        Payment payment = new Payment();
        payment.setUserId(userId);
        payment.setSubscriptionId(referenceId); // kept on entity for backward compat; null for promotions
        payment.setAmountKes(amountKes);
        payment.setAmount(chargeAmount);
        payment.setCurrency(targetCurrency);
        payment.setExchangeRate(exchangeRate);
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

        log.info("Payment initiated: id={}, method={}, amountKes={}, charged={} {}, rate={}, status={}, user={}",
                saved.getId(), method, amountKes, chargeAmount, targetCurrency,
                exchangeRate, saved.getStatus(), userId);
        return saved;
    }

    /**
     * Overload — defaults to KES when no currency is specified.
     */
    @Transactional
    public Payment processPayment(String userId,
                                  String referenceId,
                                  BigDecimal amountKes,
                                  PaymentMethod method) {
        return processPayment(userId, referenceId, amountKes, method, CurrencyService.BASE_CURRENCY);
    }

    // ====================== M-PESA CALLBACK ======================

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
            String resultDesc        = (String) stkCallback.get("ResultDesc");

            if (checkoutRequestId == null || checkoutRequestId.isBlank()) {
                log.warn("M-Pesa callback: CheckoutRequestID is missing — cannot match payment.");
                return;
            }

            boolean isSuccess = "0".equals(String.valueOf(resultCodeObj));
            PaymentStatus status = isSuccess ? PaymentStatus.COMPLETED : PaymentStatus.FAILED;

            List<Payment> payments = paymentRepository.findByTransactionRef(checkoutRequestId);
            if (payments.isEmpty()) {
                log.warn("M-Pesa callback: no payment found for CheckoutRequestID={}", checkoutRequestId);
                return;
            }

            Payment payment = payments.get(0);

            // Idempotency guard
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                log.info("M-Pesa callback: payment {} already COMPLETED — skipping.", payment.getId());
                return;
            }

            payment.setStatus(status);
            payment.setPaidAt(LocalDateTime.now());
            paymentRepository.save(payment);

            if (isSuccess) {
                log.info("M-Pesa payment CONFIRMED: paymentId={}, ref={}, amountKes={}",
                        payment.getId(), checkoutRequestId, payment.getAmountKes());
                notifyPaymentConfirmed(payment);
            } else {
                log.warn("M-Pesa payment FAILED: ref={}, reason={}", checkoutRequestId, resultDesc);
            }

        } catch (Exception e) {
            log.error("Unexpected error processing M-Pesa callback: {}", e.getMessage(), e);
        }
    }

    private void notifyPaymentConfirmed(Payment payment) {
        try {
            adPromotionService.activatePromotionAfterMpesaPayment(payment.getId());
        } catch (Exception e) {
            log.error("Failed to activate promotion after payment {}: {}", payment.getId(), e.getMessage(), e);
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