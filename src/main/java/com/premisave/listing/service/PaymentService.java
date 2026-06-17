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
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(LocalDateTime.now());
        payment.setTransactionRef("TXN-" + System.currentTimeMillis());

        Payment savedPayment = paymentRepository.save(payment);

        // Create receipt
        createPaymentReceipt(savedPayment);

        log.info("Payment processed successfully for user: {}, amount: {}", userId, amount);
        return savedPayment;
    }

    private void createPaymentReceipt(Payment payment) {
        PaymentReceipt receipt = new PaymentReceipt();
        receipt.setPaymentId(payment.getId());
        receipt.setUserId(payment.getUserId());
        receipt.setReceiptNumber("RCPT-" + System.currentTimeMillis());
        receipt.setReceiptUrl("https://premisave.com/receipts/" + receipt.getReceiptNumber()); // TODO: Generate real PDF

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