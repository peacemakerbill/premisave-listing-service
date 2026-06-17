package com.premisave.listing.service;

import com.premisave.listing.entity.Payment;
import com.premisave.listing.entity.PaymentReceipt;
import com.premisave.listing.enums.PaymentMethod;
import com.premisave.listing.enums.PaymentStatus;
import com.premisave.listing.repository.PaymentReceiptRepository;
import com.premisave.listing.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final PaymentReceiptRepository paymentReceiptRepository;

    @Transactional
    public Payment processPayment(String userId, String subscriptionId, BigDecimal amount, PaymentMethod method) {
        Payment payment = new Payment();
        payment.setUserId(userId);
        payment.setSubscriptionId(subscriptionId);
        payment.setAmount(amount);
        payment.setMethod(method);
        payment.setStatus(method == PaymentMethod.MPESA ? PaymentStatus.PENDING : PaymentStatus.COMPLETED);
        payment.setPaidAt(method == PaymentMethod.MPESA ? null : LocalDateTime.now());
        payment.setTransactionRef("TXN-" + System.currentTimeMillis());

        Payment savedPayment = paymentRepository.save(payment);
        if (method != PaymentMethod.MPESA) {
            createPaymentReceipt(savedPayment);
        }

        log.info("Payment initiated via {} for user: {}, amount: {}", method, userId, amount);
        return savedPayment;
    }

    @Transactional
    public void handleMpesaCallback(Map<String, Object> callbackPayload) {
        try {
            Map<String, Object> body = (Map<String, Object>) callbackPayload.get("Body");
            Map<String, Object> stkCallback = (Map<String, Object>) body.get("stkCallback");

            String resultCode = String.valueOf(stkCallback.get("ResultCode"));
            String checkoutRequestId = (String) stkCallback.get("CheckoutRequestID");
            String resultDesc = (String) stkCallback.get("ResultDesc");

            PaymentStatus status = "0".equals(resultCode) ? PaymentStatus.COMPLETED : PaymentStatus.FAILED;

            List<Payment> payments = paymentRepository.findByTransactionRef(checkoutRequestId);
            
            if (!payments.isEmpty()) {
                Payment payment = payments.get(0);
                payment.setStatus(status);
                payment.setPaidAt(LocalDateTime.now());
                paymentRepository.save(payment);

                if (status == PaymentStatus.COMPLETED) {
                    createPaymentReceipt(payment);
                    log.info("M-Pesa payment successful for transaction: {}", checkoutRequestId);
                } else {
                    log.warn("M-Pesa payment failed: {} - {}", checkoutRequestId, resultDesc);
                }
            }
        } catch (Exception e) {
            log.error("Error processing M-Pesa callback: {}", e.getMessage(), e);
        }
    }

    private void createPaymentReceipt(Payment payment) {
        PaymentReceipt receipt = new PaymentReceipt();
        receipt.setPaymentId(payment.getId());
        receipt.setUserId(payment.getUserId());
        receipt.setReceiptNumber("RCPT-" + System.currentTimeMillis());
        receipt.setReceiptUrl("https://premisave.com/receipts/" + receipt.getReceiptNumber());
        paymentReceiptRepository.save(receipt);
    }

    public List<Payment> getUserPayments(String userId) {
        return paymentRepository.findByUserId(userId);
    }

    public Payment getPaymentById(String id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + id));
    }
}