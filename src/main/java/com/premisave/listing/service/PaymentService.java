package com.premisave.listing.service;

import com.premisave.listing.entity.Payment;
import com.premisave.listing.enums.PaymentMethod;
import com.premisave.listing.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public Payment processPayment(String userId, String subscriptionId, BigDecimal amount, PaymentMethod method) {
        Payment payment = new Payment();
        payment.setUserId(userId);
        payment.setSubscriptionId(subscriptionId);
        payment.setAmount(amount);
        payment.setMethod(method);
        // Integrate with actual gateways (PayPal, Stripe, M-Pesa, Airtel)
        payment.setStatus(com.premisave.listing.enums.PaymentStatus.COMPLETED);
        return paymentRepository.save(payment);
    }
}