package com.premisave.listing.service;

import com.premisave.listing.client.WalletServiceClient;
import com.premisave.listing.dto.ApiResponse;
import com.premisave.listing.dto.wallet_service.WalletCommissionRates;
import com.premisave.listing.exception.WalletServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Wallet-service charges a commission on top of every transfer: the sender
 * pays amount + fee, the recipient gets exactly amount. The rate is read from
 * wallet-service and cached briefly, never hard coded.
 *
 * This service only needs it to tell the tenant what fee was added to a
 * payment. Wallet-service works out and charges the fee itself, and an escrow
 * hold reports the fee it charged, so nothing here has to match its rounding
 * for money to be right.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionService {

    private final WalletServiceClient walletServiceClient;

    @Value("${wallet.commission.cache-seconds:300}")
    private long cacheSeconds;

    private volatile BigDecimal cachedRate;
    private volatile long cachedAt;

    /** The internal transfer rate as a fraction (0.01 is 1%). Throws WalletServiceUnavailableException if it cannot be known. */
    public BigDecimal internalTransferRate() {
        BigDecimal cached = cachedRate;
        long now = System.currentTimeMillis();
        if (cached != null && now - cachedAt < cacheSeconds * 1000) {
            return cached;
        }
        try {
            ApiResponse<WalletCommissionRates> response = walletServiceClient.getCommissionRates();
            WalletCommissionRates rates = response != null ? response.getData() : null;
            if (response == null || !response.isSuccess() || rates == null || rates.getInternalTransferRate() == null) {
                throw new WalletServiceUnavailableException("Payments are temporarily unavailable. Please try again shortly.");
            }
            if (rates.getChargedOn() != null && !"ON_TOP".equalsIgnoreCase(rates.getChargedOn())) {
                log.warn("wallet-service reports commission chargedOn={}, this service assumes ON_TOP.", rates.getChargedOn());
            }
            cachedRate = rates.getInternalTransferRate();
            cachedAt = now;
            return cachedRate;
        } catch (RuntimeException e) {
            if (cached != null) {
                log.warn("Could not refresh the commission rate, using the last known {}: {}", cached, e.getMessage());
                return cached;
            }
            if (e instanceof WalletServiceUnavailableException) {
                throw e;
            }
            throw new WalletServiceUnavailableException("Payments are temporarily unavailable. Please try again shortly.");
        }
    }

    /** Commission on one transfer of this amount: amount x rate, 2 decimals, half up. */
    public BigDecimal fee(BigDecimal amount) {
        return amount.multiply(internalTransferRate()).setScale(2, RoundingMode.HALF_UP);
    }
}
