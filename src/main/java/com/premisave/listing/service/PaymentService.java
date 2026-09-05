package com.premisave.listing.service;

import com.premisave.listing.client.WalletServiceClient;
import com.premisave.listing.dto.ApiResponse;
import com.premisave.listing.dto.wallet_service.WalletInternalPaymentRequest;
import com.premisave.listing.entity.Payment;
import com.premisave.listing.enums.PaymentStatus;
import com.premisave.listing.exception.WalletServiceUnavailableException;
import com.premisave.listing.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payment execution has moved entirely to wallet-service. This class no
 * longer talks to any payment gateway directly (M-Pesa, Stripe, PayPal,
 * etc.) — it only asks wallet-service to debit an existing wallet balance
 * via POST /internal/payment, and keeps a local Payment record as an audit
 * mirror.
 *
 * This is deliberately synchronous now: debiting an existing balance is a
 * different operation from funding a wallet via an external gateway (which
 * is inherently async and is wallet-service's own internal concern). That
 * removes the PENDING-then-wait-for-callback lifecycle this class used to
 * manage for M-Pesa, and the circular @Lazy dependency on AdPromotionService
 * that existed only so that callback could notify it.
 *
 * No longer has any HTTP-facing endpoints (PaymentController was removed
 * entirely) — processPayment is now purely internal plumbing, called only
 * by AdPromotionService's wallet-debit flow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String INITIATED_BY = "LISTING_SERVICE";

    private final PaymentRepository paymentRepository;
    private final WalletServiceClient walletServiceClient;

    /**
     * Debits the given user's wallet (via wallet-service) for a listing-
     * service charge and records the outcome locally.
     *
     * KNOWN GAP: this does not yet protect against a client double-submit
     * at the HTTP layer (e.g. a double-tapped "promote" button creating two
     * separate promotion attempts, each with its own fresh reference and
     * therefore its own wallet debit). Fixing that needs either an
     * idempotency key threaded through from the controller, or confirmation
     * that wallet-service's own "reference" field is enforced as unique —
     * neither was available to build against here. What IS handled: within
     * a single call, the reference is generated and the local Payment row
     * saved before the wallet call is made, so this method's own retries
     * (e.g. a caller catching a timeout and calling it again with the same
     * referenceId) reuse the same reference rather than silently minting a
     * new one — but nothing currently forces callers to do that.
     *
     * @param userId       the paying user
     * @param referenceId  optional caller-supplied id (e.g. a promotion's
     *                     own id) folded into the wallet-service reference
     * @param amountUsd    amount in USD (wallet-service's canonical currency)
     * @param service      wallet-service's "service" tag, e.g. "AD_SUBSCRIPTION"
     * @param description  human-readable description for wallet-service's records
     * @return the persisted local Payment record
     */
    @Transactional
    public Payment processPayment(String userId,
                                  String referenceId,
                                  BigDecimal amountUsd,
                                  String service,
                                  String description) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be null or blank.");
        }
        if (amountUsd == null || amountUsd.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive.");
        }
        if (service == null || service.isBlank()) {
            throw new IllegalArgumentException("service tag must be specified.");
        }

        String reference = "listing-" + (referenceId != null ? referenceId : UUID.randomUUID().toString());

        Payment payment = new Payment();
        payment.setUserId(userId);
        payment.setSubscriptionId(referenceId);
        payment.setAmountUsd(amountUsd);
        payment.setAmount(amountUsd);
        // Hardcoded rather than CurrencyService.BASE_CURRENCY: that constant
        // drives Frankfurter's FX-rate cache for converting listing prices
        // for display, a separate concern from what currency wallet-service
        // actually settles in. The two happened to both be KES before;
        // decoupling them here so a future display-currency change doesn't
        // silently change what gets sent to wallet-service too.
        payment.setCurrency("USD");
        payment.setExchangeRate(BigDecimal.ONE);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionRef(reference);
        payment = paymentRepository.save(payment);

        WalletInternalPaymentRequest request = new WalletInternalPaymentRequest(
                userId, amountUsd, service, description, INITIATED_BY, reference);

        try {
            ApiResponse<Object> response = walletServiceClient.debitForService(request);

            if (response != null && response.isSuccess()) {
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setPaidAt(LocalDateTime.now());
                log.info("Wallet debit succeeded: userId={}, amountUsd={}, service={}, reference={}",
                        userId, amountUsd, service, reference);
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                String reason = response != null ? response.getMessage() : "no response from wallet-service";
                log.warn("Wallet debit failed: userId={}, amountUsd={}, reference={}, reason={}",
                        userId, amountUsd, reference, reason);
            }
        } catch (WalletServiceUnavailableException e) {
            // Distinct from a normal failed debit (e.g. insufficient
            // funds): wallet-service itself couldn't be reached. Still
            // record the attempt as FAILED for the audit trail, but
            // propagate the exception (rather than swallowing it like the
            // generic catch below) so the caller gets an accurate "service
            // unavailable" message instead of one implying the wallet
            // itself is the problem.
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            log.warn("Wallet debit failed — service unavailable: userId={}, reference={}, reason={}",
                    userId, reference, e.getMessage());
            throw e;
        } catch (Exception e) {
            payment.setStatus(PaymentStatus.FAILED);
            log.error("Wallet debit call errored: userId={}, reference={}, error={}: {}",
                    userId, reference, e.getClass().getSimpleName(), e.getMessage());
        }

        return paymentRepository.save(payment);
    }
}